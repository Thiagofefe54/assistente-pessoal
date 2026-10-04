package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.chat.messageDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ChatTimeTest {
    @Test fun midnightUsesTheRecordedTimezone() {
        val before = Instant.parse("2026-10-04T02:59:59Z").toEpochMilli()
        val after = Instant.parse("2026-10-04T03:00:00Z").toEpochMilli()
        assertEquals("2026-10-03", messageDate(before, "America/Sao_Paulo").toString())
        assertEquals("2026-10-04", messageDate(after, "America/Sao_Paulo").toString())
        assertEquals("2026-10-04", messageDate(before, "UTC").toString())
    }
}
