package com.thiago.assistentepessoal.routine

import android.Manifest
import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.*
import androidx.work.*
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.R
import com.thiago.assistentepessoal.cloud.CloudApi
import kotlinx.coroutines.*
import org.json.*
import java.time.*
import java.util.concurrent.TimeUnit

/** One private daily notice, opt-in; Android schedules the work and may defer it. */
class LifeReminders(private val context:Context){
    val prefs=context.getSharedPreferences("koiwai-life",0)
    private val work=WorkManager.getInstance(context)
    fun enabled()=prefs.getBoolean("enabled",false)
    fun account(owner:String?){
        if(prefs.getString("owner",null)!=owner){work.cancelAllWorkByTag("koi-life");NotificationManagerCompat.from(context).cancel("koi-life",3);prefs.edit().clear().putString("owner",owner).apply()}
        if(owner!=null && enabled())schedule(owner)
    }
    fun setEnabled(value:Boolean){prefs.edit().putBoolean("enabled",value).commit();work.cancelAllWorkByTag("koi-life");if(value)prefs.getString("owner",null)?.let{schedule(it);checkNow(it)} else NotificationManagerCompat.from(context).cancel("koi-life",3)}
    private fun schedule(owner:String){work.enqueueUniquePeriodicWork("koi-life-refresh",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<LifeReminderWorker>(6,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(workDataOf("owner" to owner)).addTag("koi-life").build())}
    fun checkNow(owner:String){work.enqueueUniqueWork("koi-life-check",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<LifeReminderWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(workDataOf("owner" to owner)).addTag("koi-life").build())}
    @Synchronized fun notify(owner:String,records:List<PersonalRecord>,payments:List<BillPayment>){
        val app=context.applicationContext as KoiwaiApplication
        if(!enabled() || prefs.getString("owner",null)!=owner || app.auth.account.value?.id!=owner || !app.reminders.allowed())return
        val now=ZonedDateTime.now();val today=now.toLocalDate()
        if(prefs.getString("delivered",null)==today.toString())return
        val release=if(app.reminders.prefs.getBoolean("quiet",true))quietRelease(now,app.reminders.prefs.getInt("quiet-start",1320),app.reminders.prefs.getInt("quiet-end",480))else now
        if(release>now){work.enqueueUniqueWork("koi-life-release",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<LifeReminderWorker>().setInputData(workDataOf("owner" to owner)).setInitialDelay(Duration.between(now,release).toMillis(),TimeUnit.MILLISECONDS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("koi-life").build());return}
        val bills=billOccurrences(records,payments,today.minusDays(2),today.plusDays(4)).filter{!it.paid}
        val spent=monthlySpent(records,today)
        val budgets=records.filter{it.kind=="budget" && !it.archived && it.date==today.withDayOfMonth(1).toString() && spent>0 && spent*10>=(it.cents ?: 0)*8}
        val week= today.minusDays((today.dayOfWeek.value-1).toLong())
        val count=records.count{it.kind=="diary" && !it.archived && it.date!=null && it.date>=week.toString() && it.date<=today.toString()}
        val weekly=today.dayOfWeek==DayOfWeek.SUNDAY && count>0
        if(bills.isEmpty() && budgets.isEmpty() && !weekly)return
        val text=buildList{
            if(bills.isNotEmpty())add("Você tem ${bills.size} vencimento(s) por conferir nos próximos dias ou recém-vencidos 💜")
            if(budgets.isNotEmpty())add("Seus gastos registrados chegaram a 80% ou mais de um limite deste mês. Vamos conferir?")
            if(weekly)add("Você guardou $count acontecimento(s) nesta semana. Seu balanço está no Diário ✨")
        }.joinToString("\n")
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("koi-life","Acompanhamento do dia",NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel("koi-life").importance==NotificationManager.IMPORTANCE_NONE)return
        val area=if(bills.isNotEmpty())"Contas" else if(budgets.isNotEmpty())"Orçamento" else "Diário"
        val open=PendingIntent.getActivity(context,3,Intent(context,MainActivity::class.java).setData(Uri.parse("koiwai://life/$area")).putExtra("openLife",area).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try{NotificationManagerCompat.from(context).notify("koi-life",3,NotificationCompat.Builder(context,"koi-life").setSmallIcon(R.drawable.ic_koi_notification).setContentTitle("Um cuidado da Koi com seu dia 💜").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(NotificationCompat.Builder(context,"koi-life").setSmallIcon(R.drawable.ic_koi_notification).setContentTitle("Koiwai").setContentText("Seu dia tem novidades para conferir.").build()).build());prefs.edit().putString("delivered",today.toString()).commit()}catch(_:SecurityException){}
    }
}

class LifeReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val app=applicationContext as KoiwaiApplication;val owner=inputData.getString("owner") ?: return Result.failure()
        if(!app.lifeReminders.enabled() || app.auth.account.value?.id!=owner)return Result.success()
        return try{
            val records=mutableListOf<PersonalRecord>();val payments=mutableListOf<BillPayment>()
            withContext(Dispatchers.IO){
                for(offset in 0 until 2000 step 100){val rows=JSONArray(CloudApi.request("/rest/v1/koi_personal_records?user_id=eq.$owner&select=*&order=updated_at.desc,id.asc&limit=100&offset=$offset",token=app.auth.token(owner)));records.addAll((0 until rows.length()).map{personalRecord(rows.getJSONObject(it))});if(rows.length()<100)break}
                for(offset in 0 until 2000 step 100){val rows=JSONArray(CloudApi.request("/rest/v1/koi_bill_payments?user_id=eq.$owner&select=*&order=occurrence_on.desc,bill_id.asc&limit=100&offset=$offset",token=app.auth.token(owner)));payments.addAll((0 until rows.length()).map{val r=rows.getJSONObject(it);BillPayment(r.getString("bill_id"),r.getString("occurrence_on"),r.getString("expense_id"))});if(rows.length()<100)break}
            }
            app.lifeReminders.notify(owner,records,payments);Result.success()
        }catch(e:Exception){if(e is CancellationException)throw e; if(runAttemptCount<2)Result.retry()else Result.failure()}
    }
}

@Composable fun LifeReminderSettings(){
    val app=LocalContext.current.applicationContext as KoiwaiApplication;val account by app.auth.account.collectAsState()
    var enabled by remember{mutableStateOf(app.lifeReminders.enabled())};var info by remember{mutableStateOf<String?>(null)}
    fun enable(value:Boolean){enabled=value;app.lifeReminders.setEnabled(value)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it && app.reminders.allowed())enable(true)else info="Permita as notificações no Android para receber os avisos."}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
        Text("Cuidados com seu dia",style=MaterialTheme.typography.titleMedium)
        Row{Text("Avisar sobre contas e orçamento",Modifier.weight(1f));Switch(checked=enabled,enabled=account!=null,onCheckedChange={if(!it)enable(false)else if(app.reminders.allowed())enable(true)else if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)else info="Permita as notificações nas configurações do Android."})}
        Text("Até um aviso por dia: vencimentos próximos, limite mensal a partir de 80% e balanço do diário no domingo. Respeita seu descanso. Precisa de internet; Android pode atrasar. Não roda com o celular desligado.",color=KoiColors.Muted)
        TextButton(enabled=enabled && account!=null,onClick={account?.id?.let{app.lifeReminders.checkNow(it)};info="Conferência solicitada. Um aviso só aparece se houver algo relevante e você ainda não recebeu o de hoje."}){Text("Conferir agora")}
        info?.let{Text(it,color=KoiColors.Blue)}
    }
}
