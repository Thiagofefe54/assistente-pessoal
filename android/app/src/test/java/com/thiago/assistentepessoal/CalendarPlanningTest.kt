package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.tools.*
import com.thiago.assistentepessoal.routine.KoiTask
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class CalendarPlanningTest {
    private val date=LocalDate.of(2026,10,10)
    private val zone=ZoneId.of("America/Sao_Paulo")
    private val now=date.minusDays(1).atStartOfDay(zone).toInstant()
    private fun instant(hour:Int)=date.atTime(hour,0).atZone(zone).toInstant()
    private fun event(id:Long,a:Int,b:Int)=PhoneCalendarEvent(id,"Agenda$id",instant(a).toEpochMilli(),instant(b).toEpochMilli(),false)
    private fun task(id:String="t",time:String?="10:00",tz:String=zone.id)=KoiTask(id,1,id,"",date.toString(),time,tz,"none",null,0,"v")
    @Test fun localQuestionsResolveAccentsAndRejectAmbiguity(){
        assertEquals(date.plusDays(1) to 2,planningQuestion("Koi, estou livre amanhã à tarde?",date))
        assertEquals(date to 1,planningQuestion("Tenho tempo hoje de manhã?",date))
        assertNull(planningQuestion("hoje ou amanhã estou livre?",date))
        assertNull(planningQuestion("estou livre hoje manhã e noite?",date))
        assertNull(planningQuestion("cria uma tarefa amanhã",date))
    }
    @Test fun overlapsAreDetectedAndBusyIntervalsAreMerged(){
        val p=calendarDayPlan(listOf(event(1,9,11),event(2,10,12)),listOf(task()),date,zone,30,8,14,now)
        assertEquals(3,p.conflicts.size)
        assertEquals(listOf(FreeWindow(instant(8),instant(9)),FreeWindow(instant(12),instant(14))),p.free)
    }
    @Test fun touchingEventsDoNotConflictAndExactInstancesAreDeduplicated(){
        val e=event(1,9,10)
        val p=calendarDayPlan(listOf(e,e,event(2,10,11)),emptyList(),date,zone,30,8,12,now)
        assertEquals(2,p.blocks.size);assertTrue(p.conflicts.isEmpty())
    }
    @Test fun identicalEventsAcrossCalendarsAreGroupedWithoutDeletingSources(){
        val a=event(1,9,10).copy(calendar="Pessoal")
        val b=a.copy(id=2,calendar="Feriados")
        val groups=calendarGroups(listOf(a,b,a.copy(begin=instant(11).toEpochMilli(),end=instant(12).toEpochMilli())))
        assertEquals(2,groups.size);assertEquals(listOf("Pessoal","Feriados"),groups.first().map{it.calendar})
        val plan=calendarDayPlan(listOf(a,b),emptyList(),date,zone,now=now)
        assertEquals(1,plan.blocks.size);assertTrue(plan.conflicts.isEmpty())
    }
    @Test fun timezoneTasksAndOvernightEventsUseInstants(){
        val e=PhoneCalendarEvent(1,"Noite",date.minusDays(1).atTime(23,0).atZone(zone).toInstant().toEpochMilli(),instant(9).toEpochMilli(),false)
        val p=calendarDayPlan(listOf(e),listOf(task(time="13:00",tz="UTC")),date,zone,30,8,14,now)
        assertEquals(instant(10),p.blocks.last().start);assertEquals(instant(9),p.free.first().start)
    }
    @Test fun allDayUsesUtcDatesAndDoesNotBlockTheWholeDay(){
        val e=PhoneCalendarEvent(1,"Feriado",date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),true)
        val p=calendarDayPlan(listOf(e),emptyList(),date,zone,30,8,22,now)
        assertEquals(listOf("Feriado"),p.allDay);assertEquals(1,p.free.size)
        assertTrue(calendarDayPlan(listOf(e),emptyList(),date.plusDays(1),zone,now=now).allDay.isEmpty())
    }
    @Test fun invalidCompletedAndArchivedTasksNeverInventAvailability(){
        val p=calendarDayPlan(emptyList(),listOf(task(time="bad"),task("done").copy(completedAt="yes"),task("archived").copy(archivedAt="yes"),task("untimed",null)),date,zone,now=now)
        assertEquals(1,p.invalidTasks);assertTrue(p.blocks.isEmpty());assertEquals(listOf("untimed"),p.untimed)
    }
    @Test fun todayNeverSuggestsElapsedTimeAndDailyRepetitionsAreLabeledAsProjections(){
        val p=calendarDayPlan(emptyList(),listOf(task().copy(date=date.minusDays(1).toString(),recurrence="daily")),date,zone,now=instant(15))
        assertEquals(1,p.blocks.size);assertTrue(p.blocks.first().source.contains("projeção"));assertEquals(instant(15),p.free.first().start)
    }
    @Test fun monthlyLastDayAndWeeklyAnchorsAreProjectedWithoutChangingTasks(){
        val month=task().copy(date="2026-01-31",recurrence="monthly")
        val feb=calendarDayPlan(emptyList(),listOf(month),LocalDate.of(2026,2,28),zone,now=Instant.EPOCH)
        assertEquals(1,feb.blocks.size);assertEquals("2026-01-31",month.date)
        val weekly=task().copy(date=date.minusDays(7).toString(),recurrence="weekly")
        assertEquals(1,calendarDayPlan(emptyList(),listOf(weekly),date,zone,now=now).blocks.size)
        assertTrue(calendarDayPlan(emptyList(),listOf(weekly),date.plusDays(1),zone,now=now).blocks.isEmpty())
    }
}
