package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import com.thiago.assistentepessoal.tools.compareBalanceWithBills

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
    @Test fun balanceAndRegisteredBillsCanBeComparedWithoutWrites()=runBlocking {
        val app=app();val owner=requireNotNull(app.auth.account.value?.id)
        val bank=assistantRequest(app,owner,"bank-summary",allowCached=false)
        val day=assistantRequest(app,owner,"day",allowCached=false)
        assertFalse(day.optBoolean("_offline"))
        LocalDate.parse(day.getString("date"))
        val rows=bank.getJSONArray("accounts");assertTrue(rows.length()>0)
        val oldest=(0 until rows.length()).map{Instant.parse(rows.getJSONObject(it).getString("provider_updated_at"))}.minOrNull()!!
        val bills=day.getJSONArray("bills_next_seven_days")
        val comparison=compareBalanceWithBills(bank.getLong("total_cents"),(0 until bills.length()).map{bills.getJSONObject(it).getLong("amount_cents")},
            day.getInt("bill_count"),bank.optBoolean("partial")||day.optBoolean("records_partial"),oldest,Instant.now())
        assertTrue(comparison.billsCents>=0)
        assertEquals(owner,app.auth.account.value?.id)
    }
}
