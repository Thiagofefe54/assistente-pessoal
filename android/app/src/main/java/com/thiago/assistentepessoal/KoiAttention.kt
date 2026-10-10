package com.thiago.assistentepessoal

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal fun attentionDue(now:Long,lastInteraction:Long,lastNotice:Long,hours:Int):Boolean =
    lastInteraction>0 && now>=lastInteraction && now-lastInteraction>=hours.coerceIn(6,48)*3_600_000L &&
        (lastNotice==0L || (now>=lastNotice && now-lastNotice>=86_400_000L))

object KoiAttention {
    fun touch(context:Context,owner:String?){if(owner!=null)context.getSharedPreferences("koi-attention",0).edit().putLong("last-$owner",System.currentTimeMillis()).apply()}
    fun schedule(context:Context,owner:String?){
        val work=WorkManager.getInstance(context)
        val prefs=context.getSharedPreferences("koi-attention",0)
        val previous=prefs.getString("owner",null)
        if(previous!=owner){previous?.let{work.cancelUniqueWork("koi-attention-$it")};prefs.edit().putString("owner",owner).commit()}
        if(owner==null)return
        if(!prefs.contains("last-$owner"))touch(context,owner)
        work.enqueueUniquePeriodicWork("koi-attention-$owner",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<KoiAttentionWorker>(6,TimeUnit.HOURS).setInputData(workDataOf("owner" to owner)).build())
    }
}

class KoiAttentionWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val app=applicationContext as KoiwaiApplication
        val owner=inputData.getString("owner") ?: return Result.success()
        if(app.auth.account.value?.id!=owner)return Result.success()
        try{
            app.database(owner).messages().trimNotices(System.currentTimeMillis()-60L*86_400_000L)
            val prefs=app.getSharedPreferences("koi-attention",0)
            if(!prefs.getBoolean("enabled-$owner",true))return Result.success()
            val now=ZonedDateTime.now()
            val quiet=app.reminders.prefs
            if(quiet.getBoolean("quiet",true) && com.thiago.assistentepessoal.routine.quietRelease(now,quiet.getInt("quiet-start",1320),quiet.getInt("quiet-end",480)).toInstant()>now.toInstant())return Result.success()
            val stamp=now.toInstant().toEpochMilli()
            if(!attentionDue(stamp,prefs.getLong("last-$owner",0),prefs.getLong("notice-$owner",0),prefs.getInt("hours-$owner",24)))return Result.success()
            val title="Um recadinho da Koi 💜"
            val text="Oi, mestre... passando para saber como você está. Quando tiver um tempinho, me conte seu dia? Sem pressa 💜"
            prefs.edit().putLong("notice-$owner",stamp).commit()
            app.recordNotice(owner,"attention-$stamp",title,text,"Carinho")
            if(app.reminders.allowed()){
                val manager=app.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("koi-attention","Recadinhos da Koi",NotificationManager.IMPORTANCE_DEFAULT))
                val open=PendingIntent.getActivity(app,744,Intent(app,MainActivity::class.java).putExtra("openChat",true).putExtra("openNotices",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                manager.notify("koi-attention-$owner",744,NotificationCompat.Builder(app,"koi-attention").setSmallIcon(R.drawable.ic_koi_notification)
                    .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
            }
            return Result.success()
        }catch(e:CancellationException){throw e}catch(_:Exception){return if(runAttemptCount<2)Result.retry() else Result.failure()}
    }
}

@Composable fun KoiAttentionPanel(){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val owner=account?.id
    val prefs=remember{app.getSharedPreferences("koi-attention",0)}
    var enabled by remember(owner){mutableStateOf(owner!=null && prefs.getBoolean("enabled-$owner",true))}
    var hours by remember(owner){mutableIntStateOf(prefs.getInt("hours-$owner",24))}
    KoiDisclosure("Um toque de carinho","Quando ficarmos um tempo sem conversar","chat",KoiColors.Purple){
        Row{Text("A Koi pode me chamar",Modifier.weight(1f));Switch(enabled,{enabled=it;owner?.let{o->prefs.edit().putBoolean("enabled-$o",it).apply()}},enabled=owner!=null)}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(6,24,48).forEach{value->FilterChip(hours==value,{hours=value;owner?.let{o->prefs.edit().putInt("hours-$o",value).apply()}},label={Text(if(value==6)"6 horas" else if(value==24)"1 dia" else "2 dias")},enabled=owner!=null)}}
        Text("No máximo um recadinho por dia, respeitando seu descanso. Você não precisa responder. O Android pode atrasar; abrir um chat ou enviar uma mensagem reinicia esse prazo.",color=KoiColors.Muted,fontSize=12.sp)
    }
}
