package com.thiago.assistentepessoal.memory

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.chat.*

@Composable
fun MemoriesScreen(onBack: () -> Unit, onAccount: () -> Unit) {
    val app = LocalContext.current.applicationContext as KoiwaiApplication
    val repo by app.memories.collectAsState()
    val chat by app.repositories.collectAsState()
    val history by chat.messages.collectAsState()
    var source by remember { mutableStateOf<String?>(null) }
    if(repo == null) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            TextButton(onClick=onBack) { Text("← Diário") }
            Text("O que fica\ncom a Koi.",fontSize=32.sp,fontWeight=FontWeight.Bold)
            Text("Entre na sua conta para confirmar e revisar lembranças.",color=KoiColors.Muted)
            KoiAction("Minha conta",onAccount,Modifier.fillMaxWidth())
        }
    } else {
        key(repo) { ConnectedMemories(repo!!,onBack,{source=it}) }
    }
    source?.let { id ->
        val original = history.orEmpty().firstOrNull { it.id==id }
        AlertDialog(onDismissRequest={source=null},title={Text("Conversa original")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                original?.let { Text("${it.localDate} • ${messageTime(it)}",color=KoiColors.Blue);Text(it.content) }
                    ?: Text("Esta conversa ainda não está disponível neste celular. Sincronize o histórico na sua conta.")
            }
        },confirmButton={TextButton(onClick={source=null}) {Text("Fechar")}})
    }
}

@Composable
private fun ConnectedMemories(repo: MemoryRepository, onBack: () -> Unit, onSource: (String) -> Unit) {
    val facts by repo.facts.collectAsState()
    val busy by repo.busy.collectAsState()
    val info by repo.info.collectAsState()
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryFact?>(null) }
    var deleting by remember { mutableStateOf<MemoryFact?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("note") }
    LaunchedEffect(repo) { repo.refresh() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick=onBack) { Text("← Diário") }
            Eyebrow("LEMBRANÇAS CONFIRMADAS",KoiColors.Purple)
            Spacer(Modifier.height(8.dp))
            Text("O que fica\ncom a Koi.",fontSize=32.sp,lineHeight=37.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Você escolhe o que ela leva para as próximas conversas.",color=KoiColors.Muted)
        }
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
                Text("${facts?.size ?: 0}/20 lembranças",fontWeight=FontWeight.SemiBold)
                Text("Ao salvar, você permite usar esta informação nas respostas e enviá-la ao provedor de IA junto da pergunta. Evite senhas e chaves de acesso.",color=KoiColors.Muted,fontSize=12.sp)
                Text("Consultar e alterar esta área requer internet. Apagar uma lembrança não apaga o diário nem retira dados de respostas anteriores.",color=KoiColors.Muted,fontSize=12.sp)
            }
            Spacer(Modifier.height(12.dp))
            KoiAction("＋ Nova lembrança",{editing=null;text="";category="note";editor=true},Modifier.fillMaxWidth(),!busy && facts!=null && facts!!.size<20)
            TextButton(onClick={repo.refresh()},enabled=!busy) { Text("Atualizar lembranças") }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=KoiColors.Purple)
            info?.let { Text(it,color=KoiColors.Blue,fontSize=13.sp) }
        }
        if(facts?.isEmpty()==true) item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple) {
                OrbitEmblem("memory",KoiColors.Purple)
                Text("Um detalhe que importa.",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                Text("Salve uma preferência, um objetivo ou algo da sua rotina. A Koi ainda não cria lembranças sozinha.",color=KoiColors.Muted)
            }
        }
        items(facts.orEmpty(),key={it.id}) { fact ->
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple) {
                Eyebrow(memoryCategories[fact.category] ?: "Lembrança",KoiColors.Purple)
                Text(fact.content,fontSize=16.sp,lineHeight=24.sp)
                Text(if(fact.sourceId==null) "Confirmada por você • anotação pessoal" else "Confirmada por você • ligada a uma conversa",color=KoiColors.Muted,fontSize=11.sp)
                Row {
                    TextButton(onClick={editing=fact;text=fact.content;category=fact.category;editor=true},enabled=!busy) {Text("Editar")}
                    TextButton(onClick={deleting=fact},enabled=!busy) {Text("Apagar",color=KoiColors.Red)}
                    fact.sourceId?.let { id -> TextButton(onClick={onSource(id)},enabled=!busy) {Text("Fonte")} }
                }
            }
        }
    }
    if(editor) MemoryEditor(text,{text=it},category,{category=it},{editor=false},saving=busy,info=info) {
        repo.save(text,category,existing=editing,onSaved={editor=false})
    }
    deleting?.let { fact -> AlertDialog(onDismissRequest={deleting=null},title={Text("Apagar lembrança?")},
        text={Text("A Koi deixará de receber esta lembrança nas próximas perguntas. A conversa original e as respostas anteriores continuam no histórico.")},
        confirmButton={TextButton(onClick={repo.delete(fact);deleting=null}) {Text("Apagar",color=KoiColors.Red)}},
        dismissButton={TextButton(onClick={deleting=null}) {Text("Cancelar")}}) }
}

@Composable
fun MemoryEditor(text: String, onText: (String)->Unit, category: String, onCategory: (String)->Unit,
    onDismiss: ()->Unit, saving: Boolean = false, info: String? = null, onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},title={Text("Confirmar lembrança")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Escreva como você quer que a Koi lembre. Até 500 caracteres.",fontSize=13.sp)
            OutlinedTextField(text,{if(it.length<=500)onText(it)},enabled=!saving,label={Text("O que a Koi deve lembrar?")},
                modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=5,shape=RoundedCornerShape(16.dp),supportingText={Text("${text.length}/500")})
            memoryCategories.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {row.forEach { (key,label) ->
                    FilterChip(selected=category==key,onClick={onCategory(key)},enabled=!saving,label={Text(label)})
                }}
            }
            Text("Será salva na sua conta e poderá ser enviada ao provedor de IA nas próximas perguntas.",fontSize=12.sp,color=KoiColors.Muted)
            info?.let {Text(it,fontSize=12.sp,color=KoiColors.Blue)}
        }
    },confirmButton={TextButton(onClick=onConfirm,enabled=!saving && text.trim().isNotEmpty() && text.length<=500) {Text(if(saving) "Salvando…" else "Confirmar e salvar")}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!saving) {Text("Cancelar")}})
}
