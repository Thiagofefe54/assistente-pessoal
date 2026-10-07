package com.thiago.assistentepessoal.memory

import android.app.*
import android.content.*
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.R
import com.thiago.assistentepessoal.routine.quietRelease
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.*
import java.util.concurrent.TimeUnit

val reportKinds=linkedMapOf("week" to "Semana","month" to "Mês","halfyear" to "Semestre","year" to "Ano")

class PeriodReports(private val context:Context) {
    val prefs=context.getSharedPreferences("koiwai-reports",Context.MODE_PRIVATE)
    private val work=WorkManager.getInstance(context)
    companion object {const val TAG="koi-reports";const val AUTO_TAG="koi-reports-auto";const val CHANNEL="koi-reports-ready"}
    fun automatic()=prefs.getBoolean("automatic",false)
    fun key(owner:String,kind:String,anchor:String)="report:$owner:$kind:${reportStart(kind,anchor)}"
    fun status(owner:String,kind:String,anchor:String)=prefs.getString(key(owner,kind,anchor),null)
    fun state(owner:String,kind:String,anchor:String,value:String) {
        if(prefs.getString("owner",null)==owner) prefs.edit().putString(key(owner,kind,anchor),value).apply()
    }
    fun account(owner:String?) {
        if(prefs.getString("owner",null)!=owner) {
            work.cancelAllWorkByTag(TAG)
            val edit=prefs.edit()
            prefs.all.keys.filter {it.startsWith("report:") || it.startsWith("delivered:") || it.startsWith("skip:") || it.startsWith("pending:")}.forEach {edit.remove(it)}
            edit.putString("owner",owner).apply()
        }
        if(owner!=null && automatic()) periodic(owner)
    }
    fun setAutomatic(value:Boolean) {
        prefs.edit().putBoolean("automatic",value).apply()
        work.cancelUniqueWork("koi-report-refresh")
        if(!value) work.cancelAllWorkByTag(AUTO_TAG)
        if(value) prefs.getString("owner",null)?.let {periodic(it)}
    }
    private fun periodic(owner:String) {
        work.enqueueUniquePeriodicWork("koi-report-refresh",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReportRefreshWorker>(24,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("owner" to owner)).addTag(TAG).build())
    }
    fun prepare(owner:String,kind:String,anchor:String,automatic:Boolean=false,continuation:Boolean=false) {
        require(kind in reportKinds || kind=="day");LocalDate.parse(anchor)
        if(prefs.getString("owner",null)!=owner) return
        if(automatic && prefs.getBoolean("skip:"+key(owner,kind,anchor),false)) return
        if(!automatic) prefs.edit().remove("skip:"+key(owner,kind,anchor)).apply()
        val builder=OneTimeWorkRequestBuilder<ReportPrepareWorker>()
            .setInputData(workDataOf("owner" to owner,"kind" to kind,"anchor" to reportStart(kind,anchor),"automatic" to automatic))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(if(continuation)15L else 0L,TimeUnit.MINUTES).addTag(TAG)
        if(automatic) builder.addTag(AUTO_TAG)
        val request=builder.build()
        if(!continuation) state(owner,kind,anchor,"Preparando capítulos e fontes…")
        work.enqueueUniqueWork(key(owner,kind,anchor),if(continuation)ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,request)
    }
    fun cancel(owner:String,kind:String,anchor:String) {
        work.cancelUniqueWork(key(owner,kind,anchor));state(owner,kind,anchor,"Preparo interrompido. Capítulos já gerados continuam salvos.")
    }
    fun suppress(owner:String,kind:String,anchor:String) {
        cancel(owner,kind,anchor)
        work.cancelUniqueWork("notify:"+key(owner,kind,anchor))
        prefs.edit().putBoolean("skip:"+key(owner,kind,anchor),true).remove("pending:"+key(owner,kind,anchor)).apply()
        NotificationManagerCompat.from(context).cancel(key(owner,kind,anchor),2)
    }
    fun ready(owner:String,kind:String,anchor:String,hash:String,automatic:Boolean=false) {
        if(prefs.getString("owner",null)!=owner) return
        if(automatic && !this.automatic()) return
        prefs.edit().putString("pending:"+key(owner,kind,anchor),hash).apply()
        state(owner,kind,anchor,"Prontinho! Atualize para ler 💜")
        notifyReady(owner,kind,anchor,hash,automatic)
    }
    fun notifyReady(owner:String,kind:String,anchor:String,hash:String,automatic:Boolean=false) {
        val app=context.applicationContext as KoiwaiApplication
        if((automatic && !this.automatic()) || prefs.getString("pending:"+key(owner,kind,anchor),null)!=hash) return
        if(app.auth.account.value?.id!=owner || !app.reminders.allowed() || !prefs.getBoolean("notify",true)) return
        val ledger="delivered:"+key(owner,kind,anchor)
        if(prefs.getString(ledger,null)==hash) return
        val now=ZonedDateTime.now()
        val release=if(app.reminders.prefs.getBoolean("quiet",true)) quietRelease(now,app.reminders.prefs.getInt("quiet-start",1320),app.reminders.prefs.getInt("quiet-end",480)) else now
        if(release.toInstant()>now.toInstant()) {
            val builder=OneTimeWorkRequestBuilder<ReportNotifyWorker>().setInputData(workDataOf("owner" to owner,"kind" to kind,"anchor" to reportStart(kind,anchor),"hash" to hash,"automatic" to automatic))
                .setInitialDelay(Duration.between(now,release).toMillis(),TimeUnit.MILLISECONDS).addTag(TAG)
            if(automatic) builder.addTag(AUTO_TAG)
            work.enqueueUniqueWork("notify:"+key(owner,kind,anchor),ExistingWorkPolicy.REPLACE,builder.build())
            return
        }
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Relatórios da Koi",NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel(CHANNEL).importance==NotificationManager.IMPORTANCE_NONE) return
        val intent=Intent(context,MainActivity::class.java).setData(Uri.parse("koiwai://report/$kind/$anchor"))
            .putExtra("reportKind",kind).putExtra("reportAnchor",anchor).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_koi_notification)
            .setContentTitle("Seu ${reportKinds[kind]?.lowercase() ?: "dia"} com a Koi está pronto 💜")
            .setContentText("Toque para ler seu relatório de $anchor e conferir as fontes.")
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_koi_notification)
                .setContentTitle("Koiwai").setContentText("Um relatório está pronto.").build()).build()
        try {NotificationManagerCompat.from(context).notify(key(owner,kind,anchor),2,notification);prefs.edit().putString(ledger,hash).apply()}
        catch(_:SecurityException) { /* Permission revoked while preparing. */ }
    }
}

class ReportPrepareWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    companion object {private val running=java.util.concurrent.atomic.AtomicBoolean(false)}
    override suspend fun doWork():Result {
        if(!running.compareAndSet(false,true)) return Result.retry()
        try {return prepareReport()} finally {running.set(false)}
    }
    private suspend fun prepareReport():Result {
        val app=applicationContext as KoiwaiApplication
        val owner=inputData.getString("owner") ?: return Result.failure()
        val kind=inputData.getString("kind") ?: return Result.failure()
        val anchor=inputData.getString("anchor") ?: return Result.failure()
        val automatic=inputData.getBoolean("automatic",false)
        if(app.auth.account.value?.id!=owner || (automatic && !app.reports.automatic())) return Result.success()
        val api=app.journal.value ?: return Result.retry()
        try {
            app.cloudSync?.run()
            repeat(3) {
                currentCoroutineContext().ensureActive()
                if(app.auth.account.value?.id!=owner || (automatic && !app.reports.automatic())) return Result.success()
                val value=if(kind=="day") api.request("/api/v1/journal/summary","POST",JSONObject().put("local_date",anchor)).put("ready",true)
                    else api.request("/api/v1/reports/prepare","POST",JSONObject().put("kind",kind).put("anchor",anchor).put("timezone",ZoneId.systemDefault().id))
                if(value.optBoolean("ready")) {
                    val report=value.getJSONObject("report")
                    app.reports.ready(owner,kind,anchor,report.getString("source_hash"),automatic);return Result.success()
                }
                if(value.optInt("available_count")==0) {app.reports.state(owner,kind,anchor,"Ainda não há mensagens sincronizadas nesse período.");return Result.success()}
                app.reports.state(owner,kind,anchor,"Preparando fontes: ${value.optInt("remaining")} capítulos principais restantes.")
            }
            app.reports.state(owner,kind,anchor,"A preparação continua em cerca de 15 min, quando o Android permitir. Você pode acompanhar aqui.")
            app.reports.prepare(owner,kind,anchor,automatic,true)
            return Result.success()
        } catch(e:Exception) {
            if(e is CancellationException)throw e
            app.reports.state(owner,kind,anchor,e.message?.take(250) ?: "Não consegui preparar agora. Você pode continuar mais tarde.")
            return Result.failure()
        }
    }
}
class ReportRefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as KoiwaiApplication
        val owner=inputData.getString("owner") ?: return Result.failure()
        if(app.auth.account.value?.id!=owner || !app.reports.automatic()) return Result.success()
        return try {
            app.cloudSync?.run()
            val pending=(app.journal.value ?: return Result.retry()).request("/api/v1/reports/due","POST",JSONObject().put("timezone",ZoneId.systemDefault().id)).getJSONArray("periods")
            var queued=0
            for(i in 0 until pending.length()) {
                val row=pending.getJSONObject(i);val kind=row.getString("kind");val anchor=row.getString("anchor")
                if(app.reports.prefs.getBoolean("skip:"+app.reports.key(owner,kind,anchor),false)) continue
                app.reports.prepare(owner,kind,anchor,true)
                if(++queued==3) break
            }
            Result.success()
        } catch(e:Exception) {if(e is CancellationException)throw e;Result.success()}
    }
}
class ReportNotifyWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as KoiwaiApplication
        val owner=inputData.getString("owner") ?: return Result.failure()
        if(app.auth.account.value?.id==owner) app.reports.notifyReady(owner,inputData.getString("kind") ?: return Result.failure(),
            inputData.getString("anchor") ?: return Result.failure(),inputData.getString("hash") ?: return Result.failure(),inputData.getBoolean("automatic",false))
        return Result.success()
    }
}
