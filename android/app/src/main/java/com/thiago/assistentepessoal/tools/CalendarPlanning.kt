package com.thiago.assistentepessoal.tools

import com.thiago.assistentepessoal.routine.KoiTask
import java.time.*
import java.text.Normalizer

internal data class PlanBlock(val key:String,val title:String,val start:Instant,val end:Instant,val source:String)
internal data class FreeWindow(val start:Instant,val end:Instant)
internal data class CalendarDayPlan(val blocks:List<PlanBlock>,val conflicts:List<Pair<PlanBlock,PlanBlock>>,
    val free:List<FreeWindow>,val untimed:List<String>,val allDay:List<String>,val invalidTasks:Int)

internal fun calendarGroups(events:List<PhoneCalendarEvent>):List<List<PhoneCalendarEvent>> =
    events.distinctBy{listOf(it.id,it.begin,it.end)}.groupBy{listOf(it.title,it.begin,it.end,it.allDay)}.values.toList()

internal fun planningQuestion(text:String,today:LocalDate):Pair<LocalDate,Int>? {
    val q=Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    if(!Regex("\\b(livre|livres|horario|horarios|tempo|agenda|encaixar)\\b").containsMatchIn(q))return null
    val tomorrow=Regex("\\bamanha\\b").containsMatchIn(q);val current=Regex("\\bhoje\\b").containsMatchIn(q)
    if(tomorrow && current)return null
    val date=when{tomorrow->today.plusDays(1);current->today;else->return null}
    val periods=listOf("manha","tarde","noite").mapIndexedNotNull{i,w->if(Regex("\\b$w\\b").containsMatchIn(q))i+1 else null}
    if(periods.size>1)return null
    return date to (periods.firstOrNull()?:0)
}

/** Read-only estimate. Tasks have no duration: the user chooses a common estimate. */
internal fun calendarDayPlan(events:List<PhoneCalendarEvent>,tasks:List<KoiTask>,day:LocalDate,
    zone:ZoneId,taskMinutes:Int=30,fromHour:Int=8,toHour:Int=22,now:Instant=Instant.now()):CalendarDayPlan {
    require(taskMinutes in 1..240 && fromHour in 0..23 && toHour in 1..24 && fromHour<toHour)
    val start=day.atStartOfDay(zone).toInstant();val end=day.plusDays(1).atStartOfDay(zone).toInstant()
    val blocks=mutableListOf<PlanBlock>();val untimed=mutableListOf<String>();var invalid=0
    val allDay=events.filter { it.allDay && day>=Instant.ofEpochMilli(it.begin).atZone(ZoneOffset.UTC).toLocalDate() &&
        day<Instant.ofEpochMilli(it.end).atZone(ZoneOffset.UTC).toLocalDate() }.map{it.title}.distinct()
    calendarGroups(events.filter{!it.allDay && it.end>it.begin}).forEach{group->
        val e=group.first()
        val a=Instant.ofEpochMilli(e.begin);val b=Instant.ofEpochMilli(e.end)
        if(a<end && b>start)blocks+=PlanBlock("event:${e.id}:${e.begin}",e.title,a,b,"Agenda${if(group.size>1)" · ${group.size} ocorrências iguais" else ""}")
    }
    tasks.filter{it.archivedAt==null && it.completedAt==null}.forEach{t->
        if(t.date==null){untimed+=t.title;return@forEach}
        try {
            val date=LocalDate.parse(t.date)
            if(t.time==null){if(date==day)untimed+=t.title;return@forEach}
            val time=LocalTime.parse(t.time);val taskZone=ZoneId.of(t.timezone)
            // Include the preceding date so a task ending after midnight is visible.
            var candidate=start.minusSeconds(taskMinutes*60L).atZone(taskZone).toLocalDate()
            val last=end.minusNanos(1).atZone(taskZone).toLocalDate()
            while(candidate<=last){
                val repeats=candidate>=date && when(t.recurrence){
                    "daily"->true
                    "weekly"->java.time.temporal.ChronoUnit.DAYS.between(date,candidate)%7L==0L
                    "monthly"->candidate.dayOfMonth==minOf(date.dayOfMonth,candidate.lengthOfMonth())
                    else->candidate==date
                }
                if(repeats){
                    val a=candidate.atTime(time).atZone(taskZone).toInstant();val b=a.plusSeconds(taskMinutes*60L)
                    if(a<end && b>start)blocks+=PlanBlock("task:${t.id}:$candidate",t.title,a,b,
                        "Tarefa · ${taskMinutes}min estimados${if(candidate!=date)" · projeção da repetição" else ""}")
                }
                candidate=candidate.plusDays(1)
            }
        }catch(_:DateTimeException){invalid++}
    }
    val ordered=blocks.sortedBy{it.start}
    val conflicts=mutableListOf<Pair<PlanBlock,PlanBlock>>()
    for(i in ordered.indices)for(j in i+1 until ordered.size){
        if(ordered[j].start>=ordered[i].end)break
        if(ordered[i].start<ordered[j].end)conflicts+=ordered[i] to ordered[j]
    }
    val opening=day.atTime(fromHour,0).atZone(zone).toInstant()
    val closing=if(toHour==24)end else day.atTime(toHour,0).atZone(zone).toInstant()
    var cursor=maxOf(opening,if(day==now.atZone(zone).toLocalDate())now else opening)
    val free=mutableListOf<FreeWindow>()
    ordered.forEach{b->
        if(b.end<=cursor || b.start>=closing)return@forEach
        if(b.start>cursor)free+=FreeWindow(cursor,minOf(b.start,closing))
        cursor=maxOf(cursor,b.end)
    }
    if(cursor<closing)free+=FreeWindow(cursor,closing)
    return CalendarDayPlan(ordered,conflicts,free.filter{Duration.between(it.start,it.end).toMinutes()>=15},untimed,allDay,invalid)
}
