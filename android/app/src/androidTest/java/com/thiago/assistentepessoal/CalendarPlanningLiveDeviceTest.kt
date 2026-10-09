package com.thiago.assistentepessoal

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.tools.*
import com.thiago.assistentepessoal.routine.loadTasks
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.time.*

/** Explicitly enabled, read-only. Does not log titles or write fixtures. */
class CalendarPlanningLiveDeviceTest {
    @Test fun calendarAndAccountTasksCanBeCombinedWithoutWrites()= runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("calendar-read-test")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        assumeTrue(ContextCompat.checkSelfPermission(app,Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED)
        val owner=requireNotNull(app.auth.account.value?.id){"Entre na conta para testar a consulta."}
        val events=phoneCalendarWeek(app)
        val tasks=loadTasks(app.auth,owner)
        assertEquals(owner,app.auth.account.value?.id)
        assertTrue(events.size<=201)
        val zone=ZoneId.systemDefault()
        for(offset in 0..6){
            val plan=calendarDayPlan(events.take(200),tasks,LocalDate.now().plusDays(offset.toLong()),zone)
            assertTrue(plan.blocks.all{it.end>it.start})
            assertTrue(plan.free.all{it.end>it.start})
            assertEquals(plan.blocks.sortedBy{it.start},plan.blocks)
        }
    }
}
