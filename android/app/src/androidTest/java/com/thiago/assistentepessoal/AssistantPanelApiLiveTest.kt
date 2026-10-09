package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Uses exactly the app's read client and existing session, without opening any activity. */
class AssistantPanelApiLiveTest {
    @Test fun authenticatedPanelClientReadsDayAndPointsWithoutGeneration() = runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLivePanel") == "true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val owner=requireNotNull(app.auth.account.value) { "Entre na conta antes do teste." }.id
        val day=readAssistant(app,owner,false)
        assertEquals(java.time.LocalDate.now().toString(),day.getString("date"))
        assertTrue(day.getInt("tasks_pending_today") >= 0)
        assertTrue(day.getJSONArray("tasks").length() <= 8)
        assertTrue(day.getJSONObject("recorded_cents").getLong("income") >= 0)
        assertNotNull(day.getJSONObject("comparison"))
        val usage=readAssistant(app,owner,true)
        assertEquals("poe",usage.getString("provider"))
        assertTrue(usage.getLong("available_points") >= 0)
        assertTrue(usage.getBoolean("shared"))
        assertFalse(usage.has("api_key")); assertFalse(usage.has("history"))
        assertEquals(owner,app.auth.account.value?.id)
    }
}
