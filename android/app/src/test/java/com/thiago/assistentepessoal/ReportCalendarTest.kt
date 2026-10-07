package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.memory.reportStart
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCalendarTest {
    @Test fun weekCrossesYearWithoutDuplicateIdentity() {
        assertEquals("2025-12-29",reportStart("week","2026-01-01"))
        assertEquals(reportStart("week","2025-12-30"),reportStart("week","2026-01-04"))
    }
    @Test fun monthsAndPartialFirstYearShareCalendarIdentity() {
        assertEquals("2024-02-01",reportStart("month","2024-02-29"))
        assertEquals("2026-07-01",reportStart("halfyear","2026-10-07"))
        assertEquals("2026-01-01",reportStart("year","2026-10-07"))
        assertEquals("2026-01-01",reportStart("halfyear","2026-06-30"))
    }
}
