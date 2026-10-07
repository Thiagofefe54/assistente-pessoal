package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.chat.*
import com.thiago.assistentepessoal.cloud.CloudApi
import com.thiago.assistentepessoal.memory.JournalRepository
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

class PackTwoLiveTest {
    private fun app()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
    @Test fun reportHasSourcesCachesAndDetectsLateMessages()=runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend")=="true")
        val app=app();val owner=requireNotNull(app.auth.account.value).id
        suspend fun request(p:String,m:String="GET",body:JSONObject?=null)=CloudApi.request(p,m,body?.toString(),app.auth.token(owner),"return=representation")
        val day="1901-02-03";val month="1901-02-01"
        val ids=listOf("f24fc19d-df0b-4ec9-a3ee-1d240e02b311","f24fc19d-df0b-4ec9-a3ee-1d240e02b312")
        assumeTrue(JSONArray(request("/rest/v1/chat_messages?select=id&user_id=eq.$owner&local_date=eq.$day&limit=1")).length()==0)
        assumeTrue(JSONArray(request("/rest/v1/koi_daily_reports?select=local_date&user_id=eq.$owner&local_date=eq.$day")).length()==0)
        assumeTrue(JSONArray(request("/rest/v1/koi_period_reports?select=start_date&user_id=eq.$owner&kind=eq.month&start_date=eq.$month")).length()==0)
        val journal=JournalRepository(app.auth,owner)
        suspend fun insert(id:String,hour:String,text:String) {
            request("/rest/v1/chat_messages","POST",JSONObject().put("user_id",owner).put("id",id).put("role","user")
                .put("content",text).put("occurred_at","${day}T$hour:00:00Z").put("timezone","UTC"))
        }
        try {
            insert(ids[0],"09","Cenário fictício PACK2_REPORT_B311: planejei estudar pintura, mas ainda não estudei.")
            val payload=JSONObject().put("kind","month").put("anchor",day).put("timezone","UTC")
            assertEquals(1,journal.request("/api/v1/reports/read","POST",payload).getInt("available_count"))
            var result=journal.request("/api/v1/reports/prepare","POST",payload)
            repeat(2) {if(!result.getBoolean("ready")) result=journal.request("/api/v1/reports/prepare","POST",payload)}
            assertTrue(result.getBoolean("ready"))
            val saved=result.getJSONObject("report");assertEquals(1,saved.getInt("source_count"))
            assertEquals(day,saved.getJSONObject("coverage").getString("first_day"))
            val items=saved.getJSONArray("items");assertTrue(items.length()>0)
            for(i in 0 until items.length()) {
                val keys=items.getJSONObject(i).getJSONArray("source_keys");assertTrue(keys.length()>0)
                for(j in 0 until keys.length()) assertEquals("day:$day",keys.getString(j))
            }
            val cached=journal.request("/api/v1/reports/prepare","POST",payload).getJSONObject("report")
            assertEquals(saved.getString("updated_at"),cached.getString("updated_at"))
            insert(ids[1],"10","Cenário fictício PACK2_REPORT_B311: planejei desenhar um barco azul amanhã; ainda não desenhei.")
            assertTrue(journal.request("/api/v1/reports/read","POST",payload).getBoolean("stale"))
        } finally {
            try {journal.request("/api/v1/reports/month/$day","DELETE")} finally {journal.request("/api/v1/journal/$day","DELETE");journal.close()}
        }
        // Immutable cloud fixture rows are removed with the guarded SQL cleanup.
    }

    @Test fun cleanupLocalReportFixture()=runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiCleanupFixture")=="true")
        val app=app();val owner=requireNotNull(app.auth.account.value).id
        val db=ChatDatabase.open(app,owner)
        try {db.openHelper.writableDatabase.execSQL("DELETE FROM messages WHERE id IN (?,?)",arrayOf(
            "f24fc19d-df0b-4ec9-a3ee-1d240e02b311","f24fc19d-df0b-4ec9-a3ee-1d240e02b312"))}
        finally {db.close()}
    }
    @Test fun directCreationCompletionRetryAndUndoAreReal()=runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend")=="true")
        val app=app();val owner=requireNotNull(app.auth.account.value).id
        val create="f24fc19d-df0b-4ec9-a3ee-1d240e02b301";val complete="f24fc19d-df0b-4ec9-a3ee-1d240e02b302"
        suspend fun request(p:String,m:String="GET")=CloudApi.request(p,m,token=app.auth.token(owner),prefer="return=representation")
        assumeTrue(JSONArray(request("/rest/v1/koi_action_receipts?select=request_id&user_id=eq.$owner&request_id=in.($create,$complete)")).length()==0)
        val backend=ChatBackend {app.auth.token(owner)}
        val message="Crie uma tarefa fictícia chamada PACK2_TEST_B301 amanhã às 09:15 com repetição diária."
        var target:String?=null
        try {
            val first=backend.sendResult(message,requestId=create)
            assertNull(first.taskDraftJson)
            val receipt=JSONObject(requireNotNull(first.actionReceiptJson));target=receipt.getString("task_id")
            assertEquals("create",receipt.getString("type"))
            val again=backend.sendResult(message,requestId=create)
            assertEquals(first.reply,again.reply)
            val path="/rest/v1/koi_tasks?select=id,title,due_date,due_time,completed_count&user_id=eq.$owner&id=eq.$target"
            val original=JSONArray(request(path)).getJSONObject(0)
            assertTrue(original.getString("title").contains("PACK2_TEST_B301"))
            assertEquals(LocalDate.now().plusDays(1).toString(),original.getString("due_date"))
            assertEquals("09:15:00",original.getString("due_time"))
            val doneMessage="Conclua a tarefa PACK2_TEST_B301."
            val done=backend.sendResult(doneMessage,requestId=complete)
            assertEquals("complete",JSONObject(requireNotNull(done.actionReceiptJson)).getString("type"))
            backend.sendResult(doneMessage,requestId=complete)
            val advanced=JSONArray(request(path)).getJSONObject(0)
            assertEquals(1,advanced.getInt("completed_count"))
            assertEquals(LocalDate.now().plusDays(2).toString(),advanced.getString("due_date"))
            backend.undo(complete);backend.undo(complete)
            val restored=JSONArray(request(path)).getJSONObject(0)
            assertEquals(0,restored.getInt("completed_count"));assertEquals(original.getString("due_date"),restored.getString("due_date"))
        } finally {
            target?.let {request("/rest/v1/koi_tasks?user_id=eq.$owner&id=eq.$it","DELETE")}
        }
        target?.let {assertEquals(0,JSONArray(request("/rest/v1/koi_tasks?select=id&user_id=eq.$owner&id=eq.$it")).length())}
    }
}
