package com.thiago.assistentepessoal.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.chat.*

@Composable
fun JournalPanel(day: String, messages: List<ChatMessage>) {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val repo by app.journal.collectAsState()
    val memory by app.memories.collectAsState()
    if(repo==null) {
        KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {Text("Entre na sua conta para preparar resumos e sugestões.",color=KoiColors.Muted)}
        return
    }
    key(repo,day) {ConnectedJournal(repo!!,memory,day,messages)}
}

@Composable
private fun ConnectedJournal(repo: JournalRepository, memory: MemoryRepository?, day: String, messages: List<ChatMessage>) {
    val collected by repo.state.collectAsState()
    val state=if(collected.day==day) collected else JournalState(day=day,busy=true)
    val memoryBusy=memory?.busy?.collectAsState()?.value ?: false
    val memoryInfo=memory?.info?.collectAsState()?.value
    var source by remember {mutableStateOf<List<String>?>(null)}
    var candidate by remember {mutableStateOf<JournalItem?>(null)}
    var text by remember {mutableStateOf("")}
    var category by remember {mutableStateOf("note")}
    var deleting by remember {mutableStateOf(false)}
    LaunchedEffect(repo,day) {repo.load(day)}
    val unsynced=messages.count {it.localDate==day && it.role=="user" && it.status==MessageStatus.SENT && !it.synced}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
            Eyebrow("SEU DIA COM A KOI",KoiColors.Blue)
            Text("Um capítulo,\ncom suas palavras.",fontSize=23.sp,fontWeight=FontWeight.SemiBold)
            Text("O resumo usa apenas suas mensagens sincronizadas deste dia, no fuso registrado em cada conversa. É uma síntese por IA: confira as fontes. Não cobre o que aconteceu fora do chat.",fontSize=12.sp,color=KoiColors.Muted)
            if(day==java.time.LocalDate.now().toString()) Text("Hoje ainda está acontecendo. Este resumo é parcial e pode ser atualizado.",fontSize=12.sp,color=KoiColors.Blue)
            if(unsynced>0) Text("$unsynced mensagens suas ainda precisam sincronizar. Abra Minha conta para sincronizar antes de gerar.",fontSize=12.sp,color=KoiColors.Red)
            if(state.stale) Text("Chegaram mensagens depois deste resumo. Atualize o capítulo.",color=KoiColors.Red,fontSize=13.sp)
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=KoiColors.Blue)
            state.info?.let {Text(it,color=KoiColors.Blue,fontSize=13.sp)}
            Row {
                TextButton(onClick={repo.summarize(day)},enabled=!state.busy && unsynced==0) {Text(if(state.report==null) "Gerar resumo" else "Atualizar resumo")}
                TextButton(onClick={repo.load(day)},enabled=!state.busy) {Text("Conferir")}
            }
            state.report?.let { report ->
                Text("Base: ${report.count} mensagens suas • Gerado/atualizado em ${runCatching {java.time.Instant.parse(report.updatedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))}.getOrDefault(report.updatedAt)}",fontSize=11.sp,color=KoiColors.Muted)
                report.items.forEach { item ->
                    Text("• ${item.text}",fontSize=14.sp)
                    TextButton(onClick={source=item.sourceIds}) {Text("Ver fontes")}
                }
                TextButton(onClick={deleting=true},enabled=!state.busy) {Text("Apagar resumo",color=KoiColors.Red)}
            }
        }
        KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple) {
            Eyebrow("O QUE VALE LEVAR COM A KOI?",KoiColors.Purple)
            Text("Sugestões de lembranças",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
            Text("Ao pedir sugestões, as mensagens sincronizadas do dia são enviadas à IA. Revise o texto e as fontes; só sua confirmação salva uma lembrança.",fontSize=12.sp,color=KoiColors.Muted)
            TextButton(onClick={repo.suggest(day)},enabled=!state.busy && !memoryBusy && unsynced==0) {Text("Encontrar lembranças neste dia")}
            state.suggestions.forEach { item ->
                HorizontalDivider(color=KoiColors.Purple.copy(alpha=.2f))
                Text(item.text,fontSize=14.sp)
                Row {
                    TextButton(onClick={candidate=item;text=item.text;category=item.category},enabled=!memoryBusy) {Text("Revisar")}
                    TextButton(onClick={source=item.sourceIds}) {Text("Fontes")}
                    TextButton(onClick={repo.dismiss(item)},enabled=!memoryBusy) {Text("Descartar")}
                }
            }
            memoryInfo?.let {Text(it,fontSize=12.sp,color=KoiColors.Blue)}
        }
    }
    source?.let { ids -> AlertDialog(onDismissRequest={source=null},title={Text("Conversas de origem")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            ids.forEach { id ->
                val message=messages.firstOrNull {it.id==id}
                if(message==null) Text("Fonte ainda não disponível neste celular. Sincronize o histórico.")
                else {Text("${message.localDate} • ${messageTime(message)}",color=KoiColors.Blue,fontSize=12.sp);Text(message.content)}
            }
        }
    },confirmButton={TextButton(onClick={source=null}) {Text("Fechar")}}) }
    candidate?.let { item -> MemoryEditor(text,{text=it},category,{category=it},{candidate=null},saving=memoryBusy,info=memoryInfo) {
        memory?.save(text,category,sourceId=item.sourceIds.first(),onSaved={repo.dismiss(item);candidate=null})
    } }
    if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("Apagar este resumo?")},text={Text("As conversas e lembranças continuam salvas. Você poderá gerar o resumo novamente.")},
        confirmButton={TextButton(onClick={repo.delete(day);deleting=false}) {Text("Apagar",color=KoiColors.Red)}},
        dismissButton={TextButton(onClick={deleting=false}) {Text("Cancelar")}})
}
