package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.chat.*
import com.thiago.assistentepessoal.cloud.CloudApi
import com.thiago.assistentepessoal.routine.TaskRepository
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class TaskChatLiveTest {
    @Test fun chatReadsLiveTasksAndCreatesOnlyAfterReviewWithoutDuplicating()=runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val owner=requireNotNull(app.auth.account.value).id
        suspend fun request(p:String,m:String="GET",b:JSONObject?=null)=CloudApi.request(p,m,b?.toString(),app.auth.token(owner),"return=representation")
        val used=JSONArray(request("/rest/v1/koi_tasks?select=slot&user_id=eq.$owner&limit=500"))
        assumeTrue("Não ampliar o teste além da lista de contexto",used.length()<50)
        val slots=(0 until used.length()).map {used.getJSONObject(it).getInt("slot")}
        val slot=(1..500).first {it !in slots}
        val fixture=UUID.randomUUID().toString();val creation=UUID.randomUUID().toString()
        val name="FarolTeste"+fixture.take(8)
        val today=LocalDate.now().toString();val tomorrow=LocalDate.now().plusDays(1).toString()
        val repo=withContext(Dispatchers.Main){TaskRepository(app.auth,owner)}
        try {
            request("/rest/v1/koi_tasks","POST",JSONObject().put("user_id",owner).put("id",fixture).put("slot",slot)
                .put("title","Tarefa fictícia $name").put("due_date",today).put("timezone",ZoneId.systemDefault().id).put("recurrence","none"))
            val backend=ChatBackend {app.auth.token(owner)}
            val answer=backend.sendResult("Qual tarefa de hoje tem $name no título? Consulte minhas tarefas registradas.")
            assertTrue("A resposta deve reconhecer a tarefa consultada",answer.reply.contains(name))
            assertNull(answer.taskDraftJson)
            val result=backend.sendResult("Crie uma tarefa fictícia chamada Revisar $name amanhã às 09:15, sem repetição.")
            val draft=requireNotNull(taskDraftFromJson(result.taskDraftJson))
            assertTrue(draft.title.contains(name));assertEquals(tomorrow,draft.date)
            assertEquals("09:15",draft.time);assertEquals("none",draft.recurrence)
            assertEquals(used.length()+1,JSONArray(request("/rest/v1/koi_tasks?select=id&user_id=eq.$owner&limit=500")).length())
            // The proposal is only data; saving uses the same reviewed app path.
            suspend fun save() {
                val confirmed=AtomicBoolean(false)
                withContext(Dispatchers.Main){repo.save(draft,creationId=creation,onSaved={confirmed.set(true)})}
                withTimeout(60000){while(repo.busy.value)delay(50)}
                assertTrue("Save must be confirmed",confirmed.get())
            }
            save();save()
            val saved=JSONArray(request("/rest/v1/koi_tasks?select=id,title,due_date,due_time&user_id=eq.$owner&id=eq.$creation"))
            assertEquals(1,saved.length());assertEquals(draft.title,saved.getJSONObject(0).getString("title"))
            assertEquals(tomorrow,saved.getJSONObject(0).getString("due_date"))
            assertEquals(used.length()+2,JSONArray(request("/rest/v1/koi_tasks?select=id&user_id=eq.$owner&limit=500")).length())
        } finally {
            withContext(Dispatchers.Main){repo.close()}
            request("/rest/v1/koi_tasks?user_id=eq.$owner&id=eq.$fixture","DELETE")
            request("/rest/v1/koi_tasks?user_id=eq.$owner&id=eq.$creation","DELETE")
        }
        assertEquals(0,JSONArray(request("/rest/v1/koi_tasks?select=id&user_id=eq.$owner&id=in.($fixture,$creation)")).length())
    }
}
