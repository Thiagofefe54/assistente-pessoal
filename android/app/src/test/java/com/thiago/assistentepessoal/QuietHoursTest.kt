package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.routine.quietRelease
import com.thiago.assistentepessoal.routine.reminderEligible
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class QuietHoursTest {
    @Test fun pastNewTasksDoNotBurstButScheduledLateTasksCanDeliver() {
        val now=java.time.Instant.parse("2026-10-07T15:00:00Z")
        assertFalse(reminderEligible(now.minusSeconds(60),now,false))
        assertTrue(reminderEligible(now.minusSeconds(60),now,true))
        assertTrue(reminderEligible(now.plusSeconds(60),now,false))
    }
    private fun at(value:String)=ZonedDateTime.parse(value+"-03:00[America/Sao_Paulo]")
    @Test fun nightCrossesMidnight() {
        assertEquals(at("2026-10-08T08:00:00"),quietRelease(at("2026-10-07T22:00:00"),1320,480))
        assertEquals(at("2026-10-08T08:00:00"),quietRelease(at("2026-10-08T03:15:20"),1320,480))
    }
    @Test fun endIsExclusiveAndDaytimeIsUnchanged() {
        val now=at("2026-10-08T08:00:00")
        assertEquals(now,quietRelease(now,1320,480))
        val daytime=at("2026-10-08T15:00:00")
        assertEquals(daytime,quietRelease(daytime,1320,480))
    }
    @Test fun daytimeQuietAndEqualEndpoints() {
        assertEquals(at("2026-10-07T14:00:00"),quietRelease(at("2026-10-07T12:30:00"),720,840))
        val now=at("2026-10-07T12:30:00")
        assertEquals(now,quietRelease(now,720,720))
    }
    @Test fun daylightSavingUsesLocalNextMorning() {
        val now=ZonedDateTime.parse("2026-03-07T23:00:00-05:00[America/New_York]")
        val morning=quietRelease(now,1320,480)
        assertEquals(8,morning.hour)
        assertEquals("-04:00",morning.offset.toString())
        assertEquals(8,java.time.Duration.between(now,morning).toHours())
    }
}
