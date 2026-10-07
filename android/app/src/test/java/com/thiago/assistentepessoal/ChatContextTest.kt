package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.chat.*
import org.junit.Assert.*
import org.junit.Test

class ChatContextTest {
    private fun message(id: String, time: Long, role: String = "user", status: String = MessageStatus.SENT,
                        text: String = id) = ChatMessage(id = id, occurredAt = time, role = role,
        status = status, content = text, timezone = "UTC")

    @Test fun excludesCurrentFailedFutureAndSystemMessagesOnRetry() {
        val current = message("current", 50, status = MessageStatus.SENDING)
        val context = recentChatContext(listOf(message("old", 10), message("reply", 20, "assistant"),
            current, message("failed", 30, status = MessageStatus.FAILED), message("future", 60),
            message("system", 40, "system")), current)
        assertEquals(listOf("old", "reply"), context.map { it.content })
    }

    @Test fun keepsMostRecentContextWithinRequestBudget() {
        val context = recentChatContext((1..30).map { message("$it", it.toLong(), text = "$it".padEnd(6000, 'x')) },
            message("current", 100))
        assertEquals(12000, context.sumOf { it.content.length })
        assertTrue(context.all { it.content.length <= 4000 })
        assertEquals(listOf("28", "29", "30"), context.map { it.content.take(2) })
    }
}
