package com.thiago.assistentepessoal

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Opt-in maintenance, never part of ordinary tests: only cancel jobs before an authorized reset. */
class PrepareAuthorizedResetTest {
    @Test fun cancelJobsForConfirmedOwner(){
        val owner=InstrumentationRegistry.getArguments().getString("resetOwner")
        assumeTrue(owner!=null && runCatching{java.util.UUID.fromString(owner)}.isSuccess && InstrumentationRegistry.getArguments().getString("confirmReset")=="YES")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        assertEquals(owner,app.auth.account.value?.id)
        WorkManager.getInstance(app).cancelAllWork().result.get(15,TimeUnit.SECONDS)
        app.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
