package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.cloud.CloudApi
import com.thiago.assistentepessoal.memory.JournalRepository
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URLEncoder
import java.util.UUID

/** Only explicitly opted-in, fictional fixtures; never exports the session. */
class PackOneLiveTest {
    private fun app()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
    @Test fun taskCrudRepeatAndDuplicateCompletionAreSafe()=runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend")=="true")
        val app=app();val owner=requireNotNull(app.auth.account.value).id
        suspend fun request(p:String,m:String="GET",b:JSONObject?=null)=CloudApi.request(p,m,b?.toString(),app.auth.token(owner),"return=representation")
        val used=JSONArray(request("/rest/v1/koi_tasks?select=slot&user_id=eq.$owner&limit=500"))
        val slots=(0 until used.length()).map {used.getJSONObject(it).getInt("slot")}
        val slot=(1..500).firstOrNull {it !in slots};assumeTrue(slot!=null)
        val id=UUID.randomUUID().toString();val path="/rest/v1/koi_tasks?user_id=eq.$owner&id=eq.$id"
        try {
            val task=JSONArray(request("/rest/v1/koi_tasks","POST",JSONObject().put("user_id",owner).put("id",id).put("slot",slot)
                .put("title","Missão fictícia Pack 1").put("due_date","2028-01-31").put("due_time","09:00").put("timezone","UTC").put("recurrence","monthly"))).getJSONObject(0)
            val old=task.getString("updated_at")
            assertEquals(1,JSONArray(request(path,"PATCH",JSONObject().put("title","Missão fictícia corrigida"))).length())
            assertEquals(0,JSONArray(request(path+"&updated_at=eq."+URLEncoder.encode(old,"UTF-8"),"PATCH",JSONObject().put("title","Edição antiga"))).length())
            val current=JSONArray(request(path)).getJSONObject(0)
            val body=JSONObject().put("task_id",id).put("expected_updated_at",current.getString("updated_at"))
            assertEquals("true",request("/rest/v1/rpc/complete_koi_task","POST",body).trim())
            assertEquals("false",request("/rest/v1/rpc/complete_koi_task","POST",body).trim())
            val after=JSONArray(request(path)).getJSONObject(0)
            assertEquals("2028-02-29",after.getString("due_date"));assertEquals(1,after.getInt("completed_count"));assertTrue(after.isNull("completed_at"))
            assertEquals(1,JSONArray(request("/rest/v1/koi_task_completions?select=id&user_id=eq.$owner&task_id=eq.$id")).length())
            assertEquals(1,JSONArray(request(path,"DELETE")).length())
            assertEquals(0,JSONArray(request("/rest/v1/koi_task_completions?select=id&user_id=eq.$owner&task_id=eq.$id")).length())
        } finally {request(path,"DELETE")}
    }

    @Test fun sourceBackedReportCachesAndDetectsLateSyncAndSuggestionsNeverSaveFacts()=runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend")=="true")
        val app=app();val owner=requireNotNull(app.auth.account.value).id
        suspend fun request(p:String,m:String="GET",b:JSONObject?=null)=CloudApi.request(p,m,b?.toString(),app.auth.token(owner),"return=representation")
        // A reserved fictional historic day, not today's real diary.
        val day="1901-01-02"
        assumeTrue("Dia reservado já possui registros; não tocar neles.",JSONArray(request("/rest/v1/chat_messages?select=id&user_id=eq.$owner&local_date=eq.$day&limit=1")).length()==0)
        assumeTrue(JSONArray(request("/rest/v1/koi_daily_reports?select=local_date&user_id=eq.$owner&local_date=eq.$day")).length()==0)
        val journal=JournalRepository(app.auth,owner)
        // Fixed, guarded fixture ids let the developer clean immutable cloud chat
        // rows with the DB connector afterwards, without widening client DELETE grants.
        val ids=listOf("f34fc19d-df0b-4ec9-a3ee-1d240e02b011","f34fc19d-df0b-4ec9-a3ee-1d240e02b012")
        var started=false
        suspend fun insert(id:String,text:String,time:String) {request("/rest/v1/chat_messages","POST",JSONObject()
            .put("user_id",owner).put("id",id).put("role","user").put("content",text).put("occurred_at","${day}T${time}:00Z").put("timezone","UTC"))}
        try {
            started=true
            insert(ids[0],"Cenário fictício: minha cor favorita é roxo e quero estudar desenho. Meu projeto imaginário chama Farol de Jade.","09:00")
            val payload=JSONObject().put("local_date",day)
            val first=journal.request("/api/v1/journal/summary","POST",payload).getJSONObject("report")
            assertEquals(1,first.getInt("source_count"))
            val items=first.getJSONArray("items");assertTrue(items.length()>0)
            for(i in 0 until items.length()) {val sources=items.getJSONObject(i).getJSONArray("source_ids");assertTrue(sources.length()>0)
                for(j in 0 until sources.length()) assertEquals(ids[0],sources.getString(j))}
            val cached=journal.request("/api/v1/journal/summary","POST",payload).getJSONObject("report")
            assertEquals(first.getString("updated_at"),cached.getString("updated_at"))
            val before=JSONArray(request("/rest/v1/memory_facts?select=id&user_id=eq.$owner&limit=20")).toString()
            val suggestions=journal.request("/api/v1/journal/suggestions","POST",payload).getJSONArray("items")
            assertTrue(suggestions.length()<=3)
            val after=JSONArray(request("/rest/v1/memory_facts?select=id&user_id=eq.$owner&limit=20")).toString()
            assertEquals(before,after)
            insert(ids[1],"Cenário fictício: hoje planejei desenhar um barco azul amanhã. Ainda não desenhei.","10:00")
            assertTrue(journal.request("/api/v1/journal/$day").getBoolean("stale"))
            val updated=journal.request("/api/v1/journal/summary","POST",payload)
            assertFalse(updated.getBoolean("stale"));assertEquals(2,updated.getJSONObject("report").getInt("source_count"))
        } finally {
            if(started) {
                journal.request("/api/v1/journal/$day","DELETE")
            }
            journal.close()
        }
    }

    @Test fun cleanupLocalJournalFixture()=runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiCleanupFixture")=="true")
        val app=app();val owner=requireNotNull(app.auth.account.value).id
        val db=com.thiago.assistentepessoal.chat.ChatDatabase.open(app,owner)
        try {db.openHelper.writableDatabase.execSQL("DELETE FROM messages WHERE id IN (?,?)",arrayOf(
            "f34fc19d-df0b-4ec9-a3ee-1d240e02b011","f34fc19d-df0b-4ec9-a3ee-1d240e02b012"))}
        finally {db.close()}
    }
}
