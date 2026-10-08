package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit, read-only check after a reset authorized by the user. */
class FreshStartTest {
    @Test fun resetLeavesNoSessionOrLocalConversation()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyFreshStart")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        assertNull(app.auth.account.value)
        val messages=withTimeout(10000){app.chatRepository.messages.filterNotNull().first()}
        assertTrue(messages.isEmpty())
    }
}
