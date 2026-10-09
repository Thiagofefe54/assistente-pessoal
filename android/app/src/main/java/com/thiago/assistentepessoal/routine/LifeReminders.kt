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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.*
import androidx.work.*
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.R
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.*
import java.util.concurrent.TimeUnit

/** Opt-in companion, one shared cap, no generation. Delivery rechecks owner and quiet. */
class LifeReminders(private val context:Context){
    val prefs=context.getSharedPreferences("koiwai-life",0)
    private val work=WorkManager.getInstance(context)
    fun enabled()=prefs.getBoolean("enabled",false)
    fun account(owner:String?){
        if(prefs.getString("owner",null)!=owner){work.cancelAllWorkByTag("koi-life");NotificationManagerCompat.from(context).cancel("koi-life",3);prefs.edit().clear().putString("owner",owner).commit()}
        if(owner!=null && enabled())schedule(owner)
    }
    fun setEnabled(value:Boolean){
        prefs.edit().putBoolean("enabled",value).commit();work.cancelAllWorkByTag("koi-life")
        if(value)prefs.getString("owner",null)?.let{schedule(it);checkNow(it)}else NotificationManagerCompat.from(context).cancel("koi-life",3)
    }
    private fun schedule(owner:String){
        work.cancelUniqueWork("koi-life-refresh")
        work.enqueueUniquePeriodicWork("koi-life-refresh-v16",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LifeReminderWorker>(1,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("owner" to owner)).addTag("koi-life").build())
    }
    fun checkNow(owner:String){
        if(!enabled())return
        work.enqueueUniqueWork("koi-life-check",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<LifeReminderWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("owner" to owner)).addTag("koi-life").build())
    }
    @Suppress("UNUSED_PARAMETER")
    @Synchronized fun notify(owner:String,records:List<PersonalRecord>,payments:List<BillPayment>){
        if(!enabled() || prefs.getString("owner",null)!=owner)return
        val now=System.currentTimeMillis()
        if(now-prefs.getLong("requested-at",0)<TimeUnit.MINUTES.toMillis(15))return
        prefs.edit().putLong("requested-at",now).apply();checkNow(owner)
    }
    fun policy()=CompanionPolicy(prefs.getInt("limit",2),prefs.getInt("morning",540),prefs.getInt("evening",1200),
        prefs.getBoolean("morning-enabled",true),prefs.getBoolean("review-enabled",true),prefs.getBoolean("alerts-enabled",true),prefs.getBoolean("affection-enabled",false))
    private fun valid(owner:String):Boolean{
        val app=context.applicationContext as KoiwaiApplication
        return enabled() && prefs.getString("owner",null)==owner && app.auth.account.value?.id==owner && app.reminders.allowed()
    }
    fun status(text:String){prefs.edit().putString("status",text).apply()}
    private fun sent(today:String)=if(prefs.getString("count-day",null)==today)prefs.getInt("count",0)else if(prefs.getString("delivered",null)==today)1 else 0
    private fun quiet(owner:String,now:ZonedDateTime):Boolean{
        val app=context.applicationContext as KoiwaiApplication
        val release=if(app.reminders.prefs.getBoolean("quiet",true))quietRelease(now,app.reminders.prefs.getInt("quiet-start",1320),app.reminders.prefs.getInt("quiet-end",480))else now
        if(release<=now)return false
        work.enqueueUniqueWork("koi-life-release",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<LifeReminderWorker>()
            .setInputData(workDataOf("owner" to owner)).setInitialDelay(Duration.between(now,release).toMillis(),TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("koi-life").build())
        status("Descanso respeitado. Próxima conferência após ${release.toLocalTime()}.");return true
    }
    @Synchronized fun shouldCheck(owner:String):Boolean{
        if(!valid(owner))return false
        val now=ZonedDateTime.now()
        if(quiet(owner,now))return false
        if(sent(now.toLocalDate().toString())>=policy().limit){status("Limite de acompanhamento de hoje atingido 💜");return false}
        val p=policy()
        return p.morningEnabled || p.reviewEnabled || p.alertsEnabled || p.affectionEnabled
    }
    @Synchronized fun deliver(owner:String,data:JSONObject):Boolean{
        if(!valid(owner))return false
        val now=ZonedDateTime.now();val today=now.toLocalDate().toString()
        if(data.getString("date")!=today){status("O dia mudou. A próxima conferência atualizará os dados.");return false}
        if(quiet(owner,now))return false
        val done=setOf("morning","review","alerts","affection").filter{prefs.getString("sent-$it",null)==today}.toSet()
        val kind=companionNotice(policy(),now.hour*60+now.minute,sent(today),(System.currentTimeMillis()-prefs.getLong("last-at",0))/3600000.0,done,data.getJSONArray("alerts").length()>0)
        if(kind==null){status("Conferido às ${now.toLocalTime().withSecond(0).withNano(0)}. Aguardando horário ou intervalo entre avisos.");return false}
        val text=when(kind){
            "review"->"Mestre, hoje você registrou ${data.getInt("completed_count")} conquista(s) e tem ${data.getInt("pending_count")} pendências. Vamos fechar o dia com carinho? 💜"
            "alerts"->data.getJSONArray("alerts").getJSONObject(0).getString("text")+" Confira os detalhes comigo 💜"
            "morning"->"Vamos cuidar do seu dia, Mestre 💜 Temos ${data.getInt("pending_count")} pendências até hoje ou sem data. Vamos escolher um pequeno passo?"
            else->listOf("Um respiro também faz parte, Mestre. Estou torcendo por você 💜","Não precisa resolver tudo agora. Um passo de cada vez, Mestre ✨","Seu esforço merece carinho, Mestre. Vamos cuidar de você também? 💜")[now.dayOfYear%3]
        }+(if(data.optBoolean("partial"))" Resumo limitado aos registros consultados." else "")
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("koi-life","Acompanhamento do dia",NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel("koi-life").importance==NotificationManager.IMPORTANCE_NONE){status("O canal de acompanhamento está bloqueado no Android.");return false}
        val open=PendingIntent.getActivity(context,3,Intent(context,MainActivity::class.java).setData(Uri.parse("koiwai://companion/day"))
            .putExtra("openLife","Meu ritmo").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try{NotificationManagerCompat.from(context).notify("koi-life",3,NotificationCompat.Builder(context,"koi-life")
            .setSmallIcon(R.drawable.ic_koi_notification).setContentTitle("A Koi está com você 💜").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(NotificationCompat.Builder(context,"koi-life")
                .setSmallIcon(R.drawable.ic_koi_notification).setContentTitle("Koiwai").setContentText("Seu acompanhamento está pronto.").build()).build())
        }catch(_:SecurityException){return false}
        prefs.edit().putString("sent-$kind",today).putString("count-day",today).putInt("count",sent(today)+1)
            .putLong("last-at",System.currentTimeMillis()).putString("status","Acompanhamento enviado às ${now.toLocalTime().withSecond(0).withNano(0)}.").commit()
        return true
    }
}

class LifeReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val app=applicationContext as KoiwaiApplication;val owner=inputData.getString("owner") ?: return Result.failure()
        if(!app.lifeReminders.shouldCheck(owner))return Result.success()
        return try{
            val data=assistantRequest(app,owner,"review",allowCached=false)
            app.lifeReminders.deliver(owner,data);Result.success()
        }catch(e:CancellationException){throw e}catch(_:Exception){
            if(app.auth.account.value?.id==owner)app.lifeReminders.status("Não consegui atualizar o acompanhamento. Vou conferir novamente com conexão.")
            if(runAttemptCount<2)Result.retry()else Result.failure()
        }
    }
}

@Composable fun LifeReminderSettings(){
    val context=LocalContext.current;val app=context.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState();val owner=account?.id
    key(owner){
        val service=app.lifeReminders;val prefs=service.prefs
        var revision by remember{mutableIntStateOf(0)}
        DisposableEffect(prefs){val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++};prefs.registerOnSharedPreferenceChangeListener(listener);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}}
        @Suppress("UNUSED_VARIABLE") val observed=revision
        val enabled=service.enabled();val policy=service.policy()
        var info by remember{mutableStateOf<String?>(null)}
        val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it && app.reminders.allowed())service.setEnabled(true)else info="Permita notificações para receber o acompanhamento."}
        fun toggle(key:String,value:Boolean){prefs.edit().putBoolean(key,value).commit();owner?.let{service.checkNow(it)}}
        fun clock(value:Int)="%02d:%02d".format(value/60,value%60)
        KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
            Text("Cuidados com seu dia",style=MaterialTheme.typography.titleMedium)
            Row{Text("A Koi me acompanha",Modifier.weight(1f));Switch(checked=enabled,enabled=owner!=null,onCheckedChange={if(!it)service.setEnabled(false)else if(app.reminders.allowed())service.setEnabled(true)else if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)else info="Permita notificações nas configurações do Android."})}
            listOf(Triple("morning-enabled","Começo do dia",policy.morningEnabled),Triple("review-enabled","Revisão do dia",policy.reviewEnabled),Triple("alerts-enabled","Contas, pendências e orçamento",policy.alertsEnabled),Triple("affection-enabled","Mensagens carinhosas",policy.affectionEnabled)).forEach{(id,label,checked)->
                Row{Text(label,Modifier.weight(1f));Switch(checked=checked,onCheckedChange={toggle(id,it)},enabled=owner!=null)}
            }
            Text("Máximo de acompanhamentos por dia",fontSize=13.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){(1..3).forEach{n->FilterChip(selected=policy.limit==n,onClick={prefs.edit().putInt("limit",n).commit()},label={Text("$n")},enabled=owner!=null)}}
            Row{
                TextButton(onClick={TimePickerDialog(context,{_,h,m->val value=h*60+m;if(value<policy.evening){prefs.edit().putInt("morning",value).commit();info=null}else info="O começo precisa ser antes da revisão."},policy.morning/60,policy.morning%60,true).show()}){Text("Começo ${clock(policy.morning)}")}
                TextButton(onClick={TimePickerDialog(context,{_,h,m->val value=h*60+m;if(value>policy.morning){prefs.edit().putInt("evening",value).commit();info=null}else info="A revisão precisa ser depois do começo."},policy.evening/60,policy.evening%60,true).show()}){Text("Revisão ${clock(policy.evening)}")}
            }
            Text("Intervalo mínimo de 4h; carinho avulso, 6h. O limite reúne estes avisos; lembretes de tarefas com horário são separados. Usa o descanso configurado em Lembretes das suas missões.",fontSize=12.sp,color=KoiColors.Muted)
            Text("Precisa de internet e deste celular ligado. Android pode atrasar. Não gasta pontos de IA. Exemplos Teste também entram no acompanhamento.",fontSize=12.sp,color=KoiColors.Muted)
            TextButton(enabled=enabled && owner!=null,onClick={owner?.let{service.checkNow(it)};info="Conferência solicitada; horários, descanso e limite continuam valendo."}){Text("Conferir agora")}
            prefs.getString("status",null)?.let{Text(it,fontSize=12.sp,color=KoiColors.Blue)}
            info?.let{Text(it,color=KoiColors.Blue)}
        }
    }
}
