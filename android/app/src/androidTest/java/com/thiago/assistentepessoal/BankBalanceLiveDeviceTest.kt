package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.UUID

/** Opt-in read-only banking check. Never exports a session or prints balances. */
class BankBalanceLiveDeviceTest {
    private fun app():KoiwaiApplication {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bank-read-test")=="true")
        return InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
    }
    @Test fun recordOwnerForPrivateConfiguration(){
        val app=app();val owner=requireNotNull(app.auth.account.value?.id){"Entre na conta Koiwai."}
        UUID.fromString(owner)
        File(app.noBackupFilesDir,"bank-check-owner.txt").writeText(owner)
    }
    @Test fun balanceReadReturnsOnlySummary()=runBlocking {
        val app=app();val owner=requireNotNull(app.auth.account.value?.id)
        val status=assistantRequest(app,owner,"bank-status",allowCached=false)
        assertTrue("Conexão precisa estar configurada.",status.getBoolean("configured"))
        val result=assistantRequest(app,owner,"bank-summary",allowCached=false)
        val rows=result.getJSONArray("accounts")
        assertTrue("Nenhuma conta BRL retornada.",rows.length()>0)
        assertFalse(result.has("transactions"));assertFalse(result.has("cpf"))
        for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i)
            assertTrue(row.has("balance_cents"));assertFalse(row.has("number"));assertFalse(row.has("id"))
        }
        assertEquals(owner,app.auth.account.value?.id)
    }
}
