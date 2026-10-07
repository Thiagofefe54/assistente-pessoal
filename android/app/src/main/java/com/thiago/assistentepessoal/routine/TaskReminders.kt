package com.thiago.assistentepessoal.routine

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.R
import com.thiago.assistentepessoal.cloud.CloudApi
import kotlinx.coroutines.*
import org.json.*
import java.time.*
import java.util.concurrent.TimeUnit

/** Opt-in, local delivery. Android may defer this work; it is not an exact alarm. */
class TaskReminders(private val context:Context) {
    val prefs=context.getSharedPreferences("koiwai-reminders",Context.MODE_PRIVATE)
    private val work=WorkManager.getInstance(context)
    private val manager=NotificationManagerCompat.from(context)
    companion object {const val CHANNEL="koi-tasks";const val TAG="koi-reminders"}
    fun enabled()=prefs.getBoolean("enabled",false)
    fun allowed():Boolean = (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled()
    fun setEnabled(value:Boolean) {
        prefs.edit().putBoolean("enabled",value).commit()
        if(!value) {
            work.cancelAllWorkByTag(TAG);manager.cancelAll()
            val edit=prefs.edit()
            prefs.all.keys.filter {it.startsWith("scheduled-") || it.startsWith("snooze-")}.forEach {edit.remove(it)}
            edit.commit()
        }
        else periodic()
    }
    @Synchronized fun account(owner:String?) {
        val previous=prefs.getString("owner",null)
        if(previous!=owner) {
            work.cancelAllWorkByTag(TAG);manager.cancelAll()
            val edit=prefs.edit()
            prefs.all.keys.filter {it.startsWith("delivered-") || it.startsWith("snooze-") || it.startsWith("scheduled-")}.forEach {edit.remove(it)}
            edit.putString("owner",owner).putString("snapshot","{}").commit()
        }
        if(owner!=null && enabled()) periodic()
    }
    private fun periodic() {
        val owner=prefs.getString("owner",null) ?: return
        work.enqueueUniquePeriodicWork("koi-reminder-refresh",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TaskReminderRefreshWorker>(1,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("owner" to owner)).addTag(TAG).build())
    }
    fun channel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Missões da Koi",NotificationManager.IMPORTANCE_DEFAULT).apply {
                description="Lembretes das tarefas com data e horário";lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            })
    }
    fun snapshot(id:String):JSONObject?=runCatching {JSONObject(prefs.getString("snapshot","{}")!!).optJSONObject(id)}.getOrNull()
    private fun registry()=runCatching{JSONObject(prefs.getString("snapshot","{}")!!)}.getOrDefault(JSONObject())
    private fun occurrence(row:JSONObject)=listOf(row.getString("date"),row.getString("time"),row.getString("timezone")).joinToString("|")
    private fun due(row:JSONObject)=LocalDateTime.of(LocalDate.parse(row.getString("date")),LocalTime.parse(row.getString("time"))).atZone(ZoneId.of(row.getString("timezone"))).toInstant()
    @Synchronized fun reconcile(owner:String,tasks:List<KoiTask>,reschedule:Boolean=false) {
        if(prefs.getString("owner",null)!=owner) return
        val old=registry();val next=JSONObject()
        tasks.filter {it.completedAt==null && it.date!=null && it.time!=null}.forEach { task ->
            val row=JSONObject().put("owner",owner).put("id",task.id).put("title",task.title)
                .put("date",task.date).put("time",task.time).put("timezone",task.timezone).put("version",task.updatedAt)
            if(runCatching{due(row)}.isSuccess) next.put(task.id,row)
        }
        val edit=prefs.edit().putString("snapshot",next.toString())
        old.keys().forEach {id -> if(!next.has(id)) {
            work.cancelUniqueWork("koi-reminder-$id");work.cancelUniqueWork("koi-complete-$id")
            manager.cancel(id,1);edit.remove("delivered-$id").remove("snooze-$id").remove("scheduled-$id")
        }}
        check(edit.commit())
        if(!enabled() || !allowed()) return
        channel()
        next.keys().forEach { id ->
            val row=next.getJSONObject(id);val previous=old.optJSONObject(id)
            val changed=previous?.optString("version")!=row.getString("version")
            if(changed) {prefs.edit().remove("snooze-$id").commit();manager.cancel(id,1)}
            if(prefs.getString("delivered-$id",null)==occurrence(row)) return@forEach
            val at=due(row)
            // Newly discovered overdue tasks do not cause a notification burst.
            if(!reminderEligible(at,Instant.now(),prefs.getString("scheduled-$id",null)==occurrence(row))) return@forEach
            val snooze=prefs.getLong("snooze-$id",0).takeIf {it>0}?.let {Instant.ofEpochMilli(it)}
            schedule(row,snooze ?: at,changed || reschedule,snooze!=null)
        }
    }
    private fun schedule(row:JSONObject,at:Instant,replace:Boolean,snoozed:Boolean=false) {
        val request=OneTimeWorkRequestBuilder<TaskReminderWorker>().setInputData(workDataOf(
            "owner" to row.getString("owner"),"task" to row.getString("id"),
            "version" to row.getString("version"),"snoozed" to snoozed))
            .setInitialDelay(Duration.between(Instant.now(),at).toMillis().coerceAtLeast(0),TimeUnit.MILLISECONDS)
            .addTag(TAG).build()
        work.enqueueUniqueWork("koi-reminder-${row.getString("id")}",if(replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,request)
        prefs.edit().putString("scheduled-${row.getString("id")}",occurrence(row)).apply()
    }
    @Synchronized fun deliver(owner:String,id:String,version:String,snoozed:Boolean):Boolean {
        if(!enabled() || !allowed() || prefs.getString("owner",null)!=owner) return false
        val row=snapshot(id) ?: return false
        if(row.getString("version")!=version) return false
        if(!snoozed && prefs.getString("delivered-$id",null)==occurrence(row)) return false
        val now=ZonedDateTime.now()
        val release=if(prefs.getBoolean("quiet",true)) quietRelease(now,prefs.getInt("quiet-start",1320),prefs.getInt("quiet-end",480)) else now
        if(release.toInstant()>now.toInstant()) {schedule(row,release.toInstant(),true,snoozed);return false}
        channel()
        val channel=context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        if(channel?.importance==NotificationManager.IMPORTANCE_NONE) return false
        try {manager.notify(id,1,notification(row,"Hora da sua missão 💜",row.getString("title"),true))}
        catch(_:SecurityException) {return false}
        prefs.edit().putString("delivered-$id",occurrence(row)).remove("snooze-$id").commit()
        return true
    }
    private fun action(row:JSONObject,name:String):PendingIntent {
        val intent=Intent(context,TaskReminderActionReceiver::class.java).setAction(name)
            .setData(Uri.parse("koiwai://task/${row.getString("id")}/$name"))
            .putExtra("owner",row.getString("owner")).putExtra("task",row.getString("id")).putExtra("version",row.getString("version"))
        return PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun notification(row:JSONObject,title:String,text:String,actions:Boolean):Notification {
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java)
            .setData(Uri.parse("koiwai://task/${row.getString("id")}"))
            .putExtra("openTasks",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_koi_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_koi_notification)
                .setContentTitle("Koiwai").setContentText("Você tem uma missão para conferir.").build())
            .apply {if(actions) {
                addAction(0,"Adiar 15 min",action(row,"snooze"));addAction(0,"Concluir",action(row,"complete"))
            }}.build()
    }
    @Synchronized fun handle(owner:String,id:String,version:String,name:String) {
        if(prefs.getString("owner",null)!=owner || !enabled()) return
        val row=snapshot(id) ?: return
        if(row.getString("version")!=version) return
        if(name=="snooze") {
            val at=Instant.now().plusSeconds(900)
            prefs.edit().putLong("snooze-$id",at.toEpochMilli()).commit()
            manager.cancel(id,1);schedule(row,at,true,true)
        } else if(name=="complete") {
            if(allowed()) safeNotify(id,notification(row,"Conferindo sua missão…","Vou confirmar a conclusão na sua conta. Precisa de internet.",false))
            work.enqueueUniqueWork("koi-complete-$id",ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TaskReminderCompleteWorker>()
                    .setInputData(workDataOf("owner" to owner,"task" to id,"version" to version))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag(TAG).build())
        }
    }
    @Synchronized fun completionFeedback(owner:String,id:String,version:String,success:Boolean) {
        if(prefs.getString("owner",null)!=owner) return
        val row=snapshot(id) ?: return
        if(row.getString("version")!=version) return
        if(success) {
            manager.cancel(id,1);work.cancelUniqueWork("koi-reminder-$id")
            val next=registry();next.remove(id)
            prefs.edit().putString("snapshot",next.toString()).remove("scheduled-$id").remove("snooze-$id").remove("delivered-$id").commit()
        }
        else if(enabled() && allowed()) safeNotify(id,notification(row,"Vamos conferir?","Não consegui confirmar a conclusão. Abra Rotina e atualize antes de tentar novamente.",false))
    }
    private fun safeNotify(id:String,notification:Notification) {
        try {manager.notify(id,1,notification)} catch(_:SecurityException) { /* Permission revoked in flight. */ }
    }
}

class TaskReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as KoiwaiApplication
        val owner=inputData.getString("owner") ?: return Result.failure()
        if(app.auth.account.value?.id!=owner) return Result.success()
        app.reminders.deliver(owner,inputData.getString("task") ?: return Result.failure(),inputData.getString("version") ?: return Result.failure(),inputData.getBoolean("snoozed",false))
        return Result.success()
    }
}
class TaskReminderRefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as KoiwaiApplication
        val owner=inputData.getString("owner") ?: return Result.failure()
        if(app.auth.account.value?.id!=owner || !app.reminders.enabled()) return Result.success()
        return try {val tasks=loadTasks(app.auth,owner);if(app.auth.account.value?.id==owner)app.reminders.reconcile(owner,tasks);Result.success()}
        catch(e:Exception) {if(e is CancellationException)throw e;Result.success()}
    }
}
class TaskReminderCompleteWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as KoiwaiApplication
        val owner=inputData.getString("owner") ?: return Result.failure()
        val id=inputData.getString("task") ?: return Result.failure()
        val version=inputData.getString("version") ?: return Result.failure()
        if(!app.reminders.enabled() || app.auth.account.value?.id!=owner || app.reminders.snapshot(id)?.optString("version")!=version) return Result.success()
        val success=try {withContext(Dispatchers.IO) {CloudApi.request("/rest/v1/rpc/complete_koi_task","POST",JSONObject()
            .put("task_id",id).put("expected_updated_at",version).toString(),app.auth.token(owner)).trim()=="true"}}
        catch(e:Exception) {if(e is CancellationException)throw e;false}
        app.reminders.completionFeedback(owner,id,version,success)
        if(success) try {val tasks=loadTasks(app.auth,owner);if(app.auth.account.value?.id==owner)app.reminders.reconcile(owner,tasks)}
        catch(e:Exception) {if(e is CancellationException)throw e}
        return Result.success()
    }
}
class TaskReminderActionReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val app=context.applicationContext as KoiwaiApplication
        val owner=intent.getStringExtra("owner") ?: return
        if(app.auth.account.value?.id!=owner) return
        app.reminders.handle(owner,intent.getStringExtra("task") ?: return,intent.getStringExtra("version") ?: return,intent.action ?: return)
    }
}
