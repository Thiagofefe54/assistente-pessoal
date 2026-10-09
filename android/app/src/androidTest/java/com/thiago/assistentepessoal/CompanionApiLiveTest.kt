package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.cloud.CloudApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Only explicit fictional fixtures, session remains in the phone, no generation. */
class CompanionApiLiveTest {
    private fun app():KoiwaiApplication {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveCompanion")=="true")
        return InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
    }
    @Test fun checkinRetryFinishAndUndoUseSameAuthenticatedClient()=runBlocking(Dispatchers.IO){
        val app=app();val owner=requireNotNull(app.auth.account.value).id
        val before=assistantRequest(app,owner,"review")
        assertEquals(LocalDate.now().toString(),before.getString("date"))
        val body=JSONObject().put("request_id",UUID.randomUUID().toString()).put("timezone",ZoneId.systemDefault().id)
            .put("category","study").put("phase","start").put("note","Dado fictício do teste V1.6.").put("test_data",true)
        val first=assistantRequest(app,owner,"checkin",body).getJSONObject("receipt")
        val id=first.getString("record_id")
        try {
            val retry=assistantRequest(app,owner,"checkin",body).getJSONObject("receipt")
            assertEquals(first.toString(),retry.toString())
            val active=assistantRequest(app,owner,"review").getJSONArray("active_checkins")
            assertTrue((0 until active.length()).any{active.getJSONObject(it).getString("id")==id})
            val finish=assistantRequest(app,owner,"checkin",JSONObject().put("request_id",UUID.randomUUID().toString())
                .put("category","study").put("phase","finish").put("record_id",id).put("expected_updated_at",first.getString("updated_at"))).getJSONObject("receipt")
            val after=assistantRequest(app,owner,"review").getJSONArray("active_checkins")
            assertFalse((0 until after.length()).any{after.getJSONObject(it).getString("id")==id})
            val undo=assistantRequest(app,owner,"undo",JSONObject().put("request_id",finish.getString("request_id")).put("target_kind","record"))
            assertTrue(undo.getJSONObject("receipt").getBoolean("undone"))
            val restored=assistantRequest(app,owner,"review").getJSONArray("active_checkins")
            assertTrue((0 until restored.length()).any{restored.getJSONObject(it).getString("id")==id})
        } finally {
            // Remove exclusively the fixture this test just created, never user records.
            CloudApi.request("/rest/v1/koi_personal_records?user_id=eq.$owner&id=eq.$id","DELETE",token=app.auth.token(owner))
        }
        assertEquals(owner,app.auth.account.value?.id)
    }
    @Test fun reschedulePreservesTimeAndCompletionAdvancesHabitWithUndo()=runBlocking(Dispatchers.IO){
        val app=app();val owner=requireNotNull(app.auth.account.value).id
        val id=UUID.randomUUID().toString();val today=LocalDate.now();val tomorrow=today.plusDays(1)
        suspend fun cloud(path:String,method:String="GET",body:JSONObject?=null)=CloudApi.request(path,method,body?.toString(),app.auth.token(owner))
        suspend fun current()=JSONArray(cloud("/rest/v1/koi_tasks?select=id,due_date,due_time,recurrence,updated_at&user_id=eq.$owner&id=eq.$id")).getJSONObject(0)
        val created=JSONObject(cloud("/rest/v1/rpc/apply_koi_action","POST",JSONObject().put("request_id",UUID.randomUUID().toString())
            .put("source_hash","a".repeat(64)).put("action","create").put("task_id",id).put("expected_updated_at",JSONObject.NULL)
            .put("fields",JSONObject().put("title","Teste — hábito fictício V1.6").put("due_date",today.toString()).put("due_time","19:00")
                .put("timezone",ZoneId.systemDefault().id).put("recurrence","daily"))))
        try {
            val original=created.getJSONObject("task")
            val moveBody=JSONObject().put("request_id",UUID.randomUUID().toString()).put("task_id",id)
                .put("expected_updated_at",original.getString("updated_at")).put("action","reschedule").put("target_date",tomorrow.toString())
            val move=assistantRequest(app,owner,"task-action",moveBody).getJSONObject("receipt")
            assertEquals(move.toString(),assistantRequest(app,owner,"task-action",moveBody).getJSONObject("receipt").toString())
            assertEquals(tomorrow.toString(),current().getString("due_date"));assertTrue(current().getString("due_time").startsWith("19:00"))
            assertEquals("daily",current().getString("recurrence"))
            assistantRequest(app,owner,"undo",JSONObject().put("request_id",move.getString("request_id")).put("target_kind","task"))
            assertEquals(today.toString(),current().getString("due_date"))
            val complete=assistantRequest(app,owner,"task-action",JSONObject().put("request_id",UUID.randomUUID().toString()).put("task_id",id)
                .put("expected_updated_at",current().getString("updated_at")).put("action","complete")).getJSONObject("receipt")
            assertEquals(tomorrow.toString(),current().getString("due_date"))
            val review=assistantRequest(app,owner,"review")
            assertTrue((0 until review.getJSONArray("habits").length()).any{review.getJSONArray("habits").getJSONObject(it).let{r->r.getString("id")==id && r.getBoolean("done_today")}})
            assistantRequest(app,owner,"undo",JSONObject().put("request_id",complete.getString("request_id")).put("target_kind","task"))
            assertEquals(today.toString(),current().getString("due_date"))
        } finally {cloud("/rest/v1/koi_tasks?user_id=eq.$owner&id=eq.$id","DELETE")}
        assertEquals(0,JSONArray(cloud("/rest/v1/koi_tasks?select=id&user_id=eq.$owner&id=eq.$id")).length())
    }
}
