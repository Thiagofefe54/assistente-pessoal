package com.thiago.assistentepessoal.routine
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
class LifeCalendarTest{
    @Test fun monthlyKeepsOriginalAnchor(){val d=LocalDate.parse("2027-01-31");assertEquals(LocalDate.parse("2027-02-28"),billOccurrence(d,"monthly",1));assertEquals(LocalDate.parse("2027-03-31"),billOccurrence(d,"monthly",2))}
    @Test fun distantAnchorStillAppears(){assertEquals(listOf(LocalDate.parse("2026-10-31")),billDays(LocalDate.parse("2000-01-31"),"monthly",LocalDate.parse("2026-10-01"),LocalDate.parse("2026-11-01")))}
    @Test fun weeklyDoesNotDrift(){assertEquals(5,billDays(LocalDate.parse("2026-10-02"),"weekly",LocalDate.parse("2026-10-01"),LocalDate.parse("2026-11-01")).size)}
    @Test fun overnightSleepRequiresEnd(){assertNull(sleepDuration("2026-10-07T23:00:00-03:00",null));assertEquals(510L,sleepDuration("2026-10-07T23:00:00-03:00","2026-10-08T07:30:00-03:00"));assertNull(sleepDuration("2026-10-07T23:00:00","2026-10-08T07:30:00"))}
    @Test fun futureAndArchivedExpensesAreExcluded(){
        val today=LocalDate.parse("2026-10-08")
        fun r(d:String,a:Boolean=false)=PersonalRecord(d,"expense","Teste","",100,0,d,a,"v")
        assertEquals(100L,monthlySpent(listOf(r("2026-10-02"),r("2026-10-20"),r("2026-10-03",true)),today))
    }
}
