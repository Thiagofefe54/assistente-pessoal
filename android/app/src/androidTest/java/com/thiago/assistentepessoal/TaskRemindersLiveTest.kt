package com.thiago.assistentepessoal

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.thiago.assistentepessoal.cloud.CloudApi
import com.thiago.assistentepessoal.routine.loadTasks
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.*
import java.util.UUID

/** Explicit opt-in; one owned fixture, no exported tokens or personal conversation. */
class TaskRemindersLiveTest {
    @Test fun scheduledNotificationQuietSnoozeAndConfirmedCompletion()=runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val reminders=app.reminders
        assertTrue("Ative os lembretes na interface antes do teste",reminders.enabled())
        assertTrue("Permita notificações na interface antes do teste",reminders.allowed())
        val owner=requireNotNull(app.auth.account.value).id
        val id=UUID.randomUUID().toString()
        val prefs=reminders.prefs
        val quiet=prefs.getBoolean("quiet",true)
        val start=prefs.getInt("quiet-start",1320);val end=prefs.getInt("quiet-end",480)
        val manager=app.getSystemService(NotificationManager::class.java)
        suspend fun request(path:String,method:String="GET",body:JSONObject?=null)=CloudApi.request(path,method,body?.toString(),app.auth.token(owner),"return=representation")
        suspend fun waitUntil(timeout:Long,check:suspend ()->Boolean) {withTimeout(timeout) {while(!check())delay(200)}}
        fun notified()=manager.activeNotifications.any {it.tag==id && it.id==1}
        try {
            val used=loadTasks(app.auth,owner).map {it.slot}
            val slot=(1..500).first {it !in used}
            val due=ZonedDateTime.now().plusSeconds(45).withNano(0)
            request("/rest/v1/koi_tasks","POST",JSONObject().put("id",id).put("user_id",owner).put("slot",slot)
                .put("title","Teste fictício de lembrete — remover após conferir")
                .put("due_date",due.toLocalDate().toString()).put("due_time",due.toLocalTime().toString())
                .put("timezone",due.zone.id).put("recurrence","none"))
            reminders.reconcile(owner,loadTasks(app.auth,owner))
            val version=requireNotNull(reminders.snapshot(id)).getString("version")
            assertFalse(reminders.deliver(UUID.randomUUID().toString(),id,version,false))
            assertFalse(reminders.deliver(owner,id,"stale-version",false))
            val now=ZonedDateTime.now();val minute=now.hour*60+now.minute
            prefs.edit().putBoolean("quiet",true).putInt("quiet-start",minute).putInt("quiet-end",(minute+60)%1440).commit()
            assertFalse(reminders.deliver(owner,id,version,false))
            assertFalse(notified())
            prefs.edit().putBoolean("quiet",false).commit()
            reminders.reconcile(owner,loadTasks(app.auth,owner),true)
            // This stage waits for actual WorkManager delivery on the phone.
            waitUntil(90000) {notified()}
            assertFalse(reminders.deliver(owner,id,version,false))
            reminders.handle(owner,id,version,"snooze")
            assertFalse(notified())
            assertTrue(prefs.getLong("snooze-$id",0)>System.currentTimeMillis()+14*60000)
            waitUntil(10000) {WorkManager.getInstance(app).getWorkInfosForUniqueWork("koi-reminder-$id").get().any {!it.state.isFinished}}
            // Exercise the re-delivery path without waiting fifteen minutes.
            assertTrue(reminders.deliver(owner,id,version,true))
            assertTrue(notified())
            reminders.handle(owner,id,version,"complete")
            waitUntil(45000) {
                val rows=JSONArray(request("/rest/v1/koi_tasks?select=completed_at&id=eq.$id&user_id=eq.$owner"))
                rows.length()==1 && !rows.getJSONObject(0).isNull("completed_at") && reminders.snapshot(id)==null
            }
            assertFalse(notified())
        } finally {
            prefs.edit().putBoolean("quiet",quiet).putInt("quiet-start",start).putInt("quiet-end",end).commit()
            withContext(NonCancellable) {
                request("/rest/v1/koi_tasks?id=eq.$id&user_id=eq.$owner","DELETE")
                reminders.reconcile(owner,loadTasks(app.auth,owner))
                manager.cancel(id,1)
                assertEquals(0,JSONArray(request("/rest/v1/koi_tasks?select=id&id=eq.$id&user_id=eq.$owner")).length())
            }
        }
    }
}
