package com.thiago.assistentepessoal.tools

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.R
import kotlinx.coroutines.CancellationException
import java.time.LocalTime
import java.util.concurrent.TimeUnit

internal fun scheduleMailAlerts(context:Context,owner:String,enabled:Boolean){
    val manager=WorkManager.getInstance(context)
    val name="koi-important-mail-$owner"
    if(!enabled){manager.cancelUniqueWork(name);context.getSystemService(NotificationManager::class.java).cancel(name,743);return}
    val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val request=PeriodicWorkRequestBuilder<GoogleMailAlertsWorker>(15,TimeUnit.MINUTES)
        .setInputData(workDataOf("owner" to owner)).setConstraints(constraints).build()
    manager.enqueueUniquePeriodicWork(name,ExistingPeriodicWorkPolicy.KEEP,request)
}

class GoogleMailAlertsWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{
        val app=applicationContext as KoiwaiApplication
        val owner=inputData.getString("owner") ?: return Result.success()
        val prefs=app.getSharedPreferences("koi-mail-alerts",0)
        if(!prefs.getBoolean("enabled-$owner",false) || app.auth.account.value?.id!=owner)return Result.success()
        return try {
            val data=assistantRequest(app,owner,"google-mail-important",allowCached=false)
            if(!prefs.getBoolean("enabled-$owner",false) || app.auth.account.value?.id!=owner)return Result.success()
            val accounts=data.getJSONArray("accounts")
            val hour=LocalTime.now().hour
            val quiet=hour>=22 || hour<8
            var total=0
            val edits=prefs.edit()
            for(i in 0 until accounts.length()){
                val account=accounts.getJSONObject(i)
                val key="$owner-${account.getString("connection_id")}";val ids=account.getJSONArray("ids")
                val current=(0 until ids.length()).map{ids.getString(it)}.toSet()
                val old=prefs.getStringSet(key,emptySet()).orEmpty()
                val new=if(prefs.contains("baseline-$key"))current-old else emptySet()
                if(!quiet)total+=new.size
                // Defer new IDs during silence, so they can be announced after 08h.
                val seen=if(quiet && prefs.contains("baseline-$key"))old.intersect(current) else current
                edits.putStringSet(key,seen).putBoolean("baseline-$key",true)
            }
            val mayNotify=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
            if(total>0){app.recordNotice(owner,"mail-${System.currentTimeMillis()}","Tem novidade importante no Gmail 💜","Há $total novo(s) e-mail(s) não lido(s) marcado(s) como importante(s) pelo Gmail. Confira em Conexões.","E-mail")}
            if(total>0 && mayNotify){
                val notifications=app.getSystemService(NotificationManager::class.java)
                notifications.createNotificationChannel(NotificationChannel("koi-important-mail","E-mails importantes",NotificationManager.IMPORTANCE_DEFAULT))
                val open=PendingIntent.getActivity(app,743,Intent(app,MainActivity::class.java).putExtra("openChat",true).putExtra("openNotices",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val note=NotificationCompat.Builder(app,"koi-important-mail").setSmallIcon(R.drawable.ic_koi_notification)
                    .setContentTitle("Koi · confira seus e-mails 💜")
                    .setContentText("Há $total novo(s) e-mail(s) não lido(s) marcado(s) como importante(s) pelo Gmail.")
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build()
                notifications.notify("koi-important-mail-$owner",743,note)
            }
            edits.putString("status-$owner",if(data.optBoolean("partial"))"Consulta parcial: alguma conta precisa ser conferida em Conexões." else "Consulta concluída; avisos importantes acompanhados.")
                .putLong("checked-$owner",System.currentTimeMillis()).apply()
            Result.success()
        }catch(e:Exception){
            if(e is CancellationException)throw e
            prefs.edit().putString("status-$owner","Não consegui consultar. Confira internet e autorizações Google.").apply()
            if(runAttemptCount<2)Result.retry() else Result.success()
        }
    }
}

@Composable fun GoogleMailAlertsPanel(){
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val owner=account?.id
    val prefs=remember{context.getSharedPreferences("koi-mail-alerts",0)}
    var enabled by remember(owner){mutableStateOf(owner!=null && prefs.getBoolean("enabled-$owner",false))}
    var info by remember{mutableStateOf<String?>(null)}
    fun activate(){if(owner!=null){enabled=true;prefs.edit().putBoolean("enabled-$owner",true).apply();scheduleMailAlerts(context,owner,true);info="Ativado. A primeira consulta define o ponto inicial; avisos antigos não são anunciados."}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)activate() else info="Permita notificações para receber os avisos."}
    KoiDisclosure("Avisos de e-mails importantes","Novidades do Gmail, sem gastar IA","chat",KoiColors.Purple){
        Text("Consulta as contas Google autorizadas aproximadamente a cada 15 minutos, quando o Android permitir. Usa a marcação Importante do Gmail, sem ler o corpo dos e-mails. Silêncio das 22h às 08h. Não é um aviso instantâneo.",fontSize=12.sp,color=KoiColors.Muted)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Receber avisos",modifier=Modifier.weight(1f));Switch(enabled,{value->
            if(owner!=null){if(!value){enabled=false;prefs.edit().putBoolean("enabled-$owner",false).apply();scheduleMailAlerts(context,owner,false)}
            else if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else activate()}
        },enabled=owner!=null)}
        if(enabled && owner!=null)TextButton(onClick={
            val request=OneTimeWorkRequestBuilder<GoogleMailAlertsWorker>().setInputData(workDataOf("owner" to owner))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("koi-mail-check-$owner",ExistingWorkPolicy.KEEP,request)
            info="Consulta solicitada. O Android executará quando houver conexão."
        }){Text("Consultar agora")}
        info?.let{Text(it,color=KoiColors.Blue,fontSize=12.sp)}
    }
}
