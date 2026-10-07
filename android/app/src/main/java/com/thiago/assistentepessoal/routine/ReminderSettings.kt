package com.thiago.assistentepessoal.routine

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thiago.assistentepessoal.*
import java.util.Locale

@Composable fun ReminderSettings() {
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val reminders=app.reminders
    val account by app.auth.account.collectAsState()
    var enabled by remember {mutableStateOf(reminders.enabled())}
    var quiet by remember {mutableStateOf(reminders.prefs.getBoolean("quiet",true))}
    var start by remember {mutableIntStateOf(reminders.prefs.getInt("quiet-start",1320))}
    var end by remember {mutableIntStateOf(reminders.prefs.getInt("quiet-end",480))}
    var info by remember {mutableStateOf<String?>(null)}
    fun enable(value:Boolean) {
        enabled=value;reminders.setEnabled(value)
        if(value) {app.tasks.value?.refresh();info="Ativado neste celular. Tarefas precisam de data e horário."}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted && reminders.allowed()) enable(true)
        else {enable(false);info="As notificações estão bloqueadas. Você pode permitir nas configurações do Android."}
    }
    fun changedQuiet() {
        reminders.prefs.edit().putBoolean("quiet",quiet).putInt("quiet-start",start).putInt("quiet-end",end).apply()
        account?.id?.let {owner->app.tasks.value?.tasks?.value?.let {reminders.reconcile(owner,it,true)}}
        app.tasks.value?.refresh()
    }
    fun clock(value:Int)=String.format(Locale.ROOT,"%02d:%02d",value/60,value%60)
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
        Text("Lembretes das suas missões",style=MaterialTheme.typography.titleMedium)
        Row {
            Text("Avisar neste celular",Modifier.weight(1f))
            Switch(checked=enabled,enabled=account!=null,onCheckedChange={ value ->
                if(!value) enable(false)
                else if(reminders.allowed()) enable(true)
                else if(Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else info="Permita as notificações da Koiwai nas configurações do Android."
            })
        }
        if(account==null) Text("Entre na sua conta para ativar.",color=KoiColors.Muted)
        Text("Avisos com Adiar 15 min e Concluir. O Android pode atrasar a entrega para economizar bateria; não é um alarme exato.",color=KoiColors.Muted)
        Row {
            Text("Respeitar horário de descanso",Modifier.weight(1f))
            Switch(checked=quiet,onCheckedChange={quiet=it;changedQuiet()})
        }
        if(quiet) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={TimePickerDialog(context,{_,h,m->start=h*60+m;changedQuiet()},start/60,start%60,true).show()}) {Text("De ${clock(start)}")}
                TextButton(onClick={TimePickerDialog(context,{_,h,m->end=h*60+m;changedQuiet()},end/60,end%60,true).show()}) {Text("Até ${clock(end)}")}
            }
            Text(if(start==end) "Horários iguais desativam o descanso." else "Durante o descanso, o aviso fica para depois. Usa o horário deste celular.",color=KoiColors.Muted)
        }
        info?.let {Text(it,color=KoiColors.Blue)}
    }
}
