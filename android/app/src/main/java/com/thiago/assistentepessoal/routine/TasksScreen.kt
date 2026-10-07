package com.thiago.assistentepessoal.routine

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

@Composable
fun TasksScreen(onBack: () -> Unit, onAccount: () -> Unit) {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val repo by app.tasks.collectAsState()
    if(repo==null) Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick=onBack) { Text("← Rotina") }
        Text("Suas missões",fontSize=32.sp,fontWeight=FontWeight.Bold)
        Text("Entre na sua conta para salvar e sincronizar tarefas.",color=KoiColors.Muted)
        KoiAction("Minha conta",onAccount,Modifier.fillMaxWidth())
    } else key(repo) { ConnectedTasks(repo!!,onBack) }
}

@Composable
private fun ConnectedTasks(repo: TaskRepository, onBack: () -> Unit) {
    val tasks by repo.tasks.collectAsState()
    val busy by repo.busy.collectAsState()
    val info by repo.info.collectAsState()
    val recent by repo.recent.collectAsState()
    var filter by rememberSaveable { mutableStateOf("pending") }
    var editor by remember { mutableStateOf(false) }
    var creationId by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var editing by remember { mutableStateOf<KoiTask?>(null) }
    var deleting by remember { mutableStateOf<KoiTask?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {while(true) {now=Instant.now();delay(30000)}}
    LaunchedEffect(repo) { repo.refresh() }
    val visible=tasks.orEmpty().filter { when(filter) {"today"->it.today(now);"done"->it.completedAt!=null;"late"->it.overdue(now);else->it.completedAt==null} }
        .sortedWith(compareBy<KoiTask> { it.date ?: "9999-12-31" }.thenBy { it.time ?: "23:59" })
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(onClick=onBack) { Text("← Rotina") }
            Eyebrow("PAINEL DE MISSÕES",KoiColors.Red)
            Text("Um passo.\nUma conquista.",fontSize=32.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold)
            Text("Seus próximos passos, salvos na sua conta.",color=KoiColors.Muted)
        }
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Red) {
                val pending=tasks.orEmpty().count { it.completedAt==null }
                Text("$pending em andamento • ${tasks.orEmpty().count { it.overdue(now) }} vencidas",fontWeight=FontWeight.SemiBold)
                Text("Datas usam o fuso da tarefa. Ative lembretes em Configurações para missões com data e horário. Consultar e alterar requer internet.",fontSize=12.sp,color=KoiColors.Muted)
            }
            Spacer(Modifier.height(10.dp))
            KoiAction("＋ Nova tarefa",{editing=null;creationId=java.util.UUID.randomUUID().toString();repo.clearInfo();editor=true},Modifier.fillMaxWidth(),!busy && tasks!=null && tasks!!.size<500)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("today" to "Hoje","pending" to "Pendentes","late" to "Vencidas","done" to "Concluídas").forEach { (id,label) ->
                    FilterChip(selected=filter==id,onClick={filter=id},label={Text(label,fontSize=11.sp)})
                }
            }
            TextButton(onClick={repo.refresh()},enabled=!busy) {Text("Atualizar tarefas")}
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=KoiColors.Red)
            info?.let {Text(it,color=KoiColors.Blue,fontSize=13.sp)}
        }
        if(tasks!=null && visible.isEmpty()) item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Red) {
                OrbitEmblem("tasks",KoiColors.Red)
                Text(if(filter=="pending") "Qual é seu próximo passo?" else "Nenhuma missão por aqui.",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                Text("Você escolhe o tamanho da missão. Um passo pequeno também conta.",color=KoiColors.Muted)
            }
        }
        items(visible,key={it.id}) { task ->
            KoiPanel(Modifier.fillMaxWidth(),accent=if(task.overdue(now)) KoiColors.Red else KoiColors.Purple) {
                Eyebrow(if(task.completedAt!=null) "CONCLUÍDA" else if(task.overdue(now)) "DATA PASSOU" else "EM ANDAMENTO",KoiColors.Red)
                Text(task.title,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                if(task.notes.isNotBlank()) Text(task.notes,color=KoiColors.Muted)
                Text(listOfNotNull(task.date?.let {LocalDate.parse(it).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))},task.time?.take(5),
                    taskRecurrences[task.recurrence],if(task.date!=null) task.timezone else null).joinToString(" • "),fontSize=12.sp,color=KoiColors.Blue)
                if(task.recurrence!="none") Text("${task.count} etapas concluídas. Cada conclusão avança uma ocorrência.",fontSize=11.sp,color=KoiColors.Muted)
                Row {
                    TextButton(onClick={if(task.completedAt==null)repo.complete(task) else repo.reopen(task)},enabled=!busy) {Text(if(task.completedAt==null) "Concluir" else "Reabrir")}
                    TextButton(onClick={editing=task;repo.clearInfo();editor=true},enabled=!busy) {Text("Editar")}
                    TextButton(onClick={deleting=task},enabled=!busy) {Text("Apagar",color=KoiColors.Red)}
                }
            }
        }
        if(recent.isNotEmpty()) item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
                Eyebrow("SUAS ÚLTIMAS CONQUISTAS",KoiColors.Blue)
                recent.forEach {Text(it,fontSize=13.sp)}
            }
        }
    }
    if(editor) TaskEditor(editing,{if(!busy)editor=false},saving=busy,info=info) {
        repo.save(it,editing,creationId=creationId,onSaved={editor=false})
    }
    deleting?.let { task -> AlertDialog(onDismissRequest={deleting=null},title={Text("Apagar tarefa?")},
        text={Text("Isso apaga “${task.title}” e seus registros de conclusão. Suas conversas continuam no diário.")},
        confirmButton={TextButton(onClick={repo.delete(task);deleting=null}) {Text("Apagar",color=KoiColors.Red)}},
        dismissButton={TextButton(onClick={deleting=null}) {Text("Cancelar")}}) }
}

@Composable
fun TaskEditor(existing: KoiTask?, onDismiss: () -> Unit, initial: TaskDraft? = null, saving: Boolean = false, info: String? = null, onSave: (TaskDraft) -> Unit) {
    val context=LocalContext.current
    var title by rememberSaveable {mutableStateOf(existing?.title ?: initial?.title ?: "")}
    var notes by rememberSaveable {mutableStateOf(existing?.notes ?: initial?.notes ?: "")}
    var date by rememberSaveable {mutableStateOf(existing?.date ?: initial?.date ?: "")}
    var time by rememberSaveable {mutableStateOf(existing?.time?.take(5) ?: initial?.time ?: "")}
    var recurrence by rememberSaveable {mutableStateOf(existing?.recurrence ?: initial?.recurrence ?: "none")}
    val draft=TaskDraft(title,notes,date,time,recurrence)
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},title={Text(if(existing==null) "Nova missão" else "Editar missão")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title,{if(it.length<=160)title=it},enabled=!saving,label={Text("Título da tarefa")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(notes,{if(it.length<=2000)notes=it},enabled=!saving,label={Text("Detalhes (opcional)")},minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth())
            Eyebrow("QUANDO É SUA MISSÃO?",KoiColors.Blue)
            TextButton(onClick={
                val initial=runCatching{LocalDate.parse(date)}.getOrDefault(LocalDate.now())
                DatePickerDialog(context,{_,y,m,d->date=LocalDate.of(y,m+1,d).toString()},initial.year,initial.monthValue-1,initial.dayOfMonth).show()
            },enabled=!saving) {Text(if(date.isBlank()) "Escolher data" else "Data: ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")}
            TextButton(onClick={
                val initialTime=runCatching{LocalTime.parse(time)}.getOrDefault(LocalTime.of(9,0))
                TimePickerDialog(context,{_,h,m->
                    time=LocalTime.of(h,m).toString()
                    if(date.isBlank()) date=LocalDate.now(java.time.ZoneId.of(existing?.timezone ?: java.time.ZoneId.systemDefault().id)).toString()
                },initialTime.hour,initialTime.minute,true).show()
            },enabled=!saving) {Text(if(time.isBlank()) "◷ Escolher horário" else "◷ Horário: $time",color=KoiColors.Blue)}
            if(date.isBlank()) Text("Se escolher apenas o horário, a data será hoje. Você poderá mudar antes de salvar.",fontSize=12.sp,color=KoiColors.Muted)
            if(date.isNotBlank()) TextButton(onClick={date="";time="";recurrence="none"},enabled=!saving) {Text("Remover data e repetição")}
            if(time.isNotBlank()) TextButton(onClick={time=""},enabled=!saving) {Text("Remover horário")}
            taskRecurrences.entries.chunked(2).forEach { entries -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                entries.forEach { (id,label)-> FilterChip(selected=recurrence==id,onClick={recurrence=id},enabled=!saving,label={Text(label)}) }
            }}
            Text("Repetições precisam de uma data. Na mensal, dias que não existem são ajustados ao fim do mês; a próxima parte do ciclo usa essa nova data.",fontSize=12.sp,color=KoiColors.Muted)
            if(existing?.completedAt!=null) Text("Escolher uma repetição reabre esta tarefa.",fontSize=12.sp,color=KoiColors.Blue)
            draft.error()?.let {Text(it,fontSize=12.sp,color=KoiColors.Muted)}
            info?.let {Text(it,fontSize=12.sp,color=KoiColors.Blue)}
        }
    },confirmButton={TextButton(onClick={onSave(draft)},enabled=!saving && draft.error()==null) {Text(if(saving) "Salvando…" else "Salvar tarefa")}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!saving) {Text("Cancelar")}})
}
