package com.thiago.assistentepessoal.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*

private val folders=listOf("Pessoal","Estudos","Trabalho","Outros")

@Composable
fun ChatScreen(onBack:()->Unit,onAccount:()->Unit,onJournal:(String)->Unit={},onTools:()->Unit={},onOpenAction:(String)->Unit={}){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val repo by app.repositories.collectAsState()
    val account by app.auth.account.collectAsState()
    val selected by repo.conversationId.collectAsState()
    var choosing by remember(repo){mutableStateOf(false)}
    val images=remember(repo){mutableMapOf<String,MutableState<String?>>() }
    val imageState=remember(repo,selected){images.getOrPut(selected){mutableStateOf(null)}}
    val state=rememberSaveableStateHolder()
    LaunchedEffect(repo,selected){KoiAttention.touch(app,app.auth.account.value?.id)}
    state.SaveableStateProvider("${account?.id ?: "local"}|$selected"){
        if(selected==NOTICES_CONVERSATION)NoticesScreen(repo,onBack,{choosing=true},onOpenAction)
        else ChatConversationScreen(onBack,onAccount,onJournal,onTools,onOpenAction,{choosing=true},imageState)
    }
    if(choosing)ConversationsSheet(repo,{choosing=false})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ConversationsSheet(repo:ChatRepository,onClose:()->Unit){
    val chats by repo.conversations.collectAsState()
    val messages by repo.allMessages.collectAsState()
    val notices by repo.notices.collectAsState()
    val selected by repo.conversationId.collectAsState()
    val busy by repo.busy.collectAsState()
    var search by remember{mutableStateOf("")}
    var folder by remember{mutableStateOf("Todas")}
    var editing by remember{mutableStateOf<ChatConversation?>(null)}
    val last=remember(messages){messages.groupBy{it.conversationId}.mapValues{it.value.maxByOrNull{m->m.occurredAt}}}
    val ordered=chats.filter{(folder=="Todas" || it.folder==folder) && (search.isBlank() || it.title.contains(search,true) || last[it.id]?.content?.contains(search,true)==true)}
        .sortedWith(compareByDescending<ChatConversation>{it.pinned}.thenByDescending{last[it.id]?.occurredAt ?: it.createdAt})
    ModalBottomSheet(onDismissRequest=onClose,containerColor=KoiColors.Ink,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            KoiPageHeading("CONVERSAS","Seu espaço com a Koi","Um lugar para cada assunto",kind="chat")
            KoiPanel(Modifier.fillMaxWidth().clickable(enabled=!busy){repo.selectConversation(NOTICES_CONVERSATION);onClose()},accent=KoiColors.Red){
                Row(verticalAlignment=Alignment.CenterVertically){
                    OrbitEmblem("spark",KoiColors.Red,Modifier.size(44.dp))
                    Column(Modifier.weight(1f).padding(start=12.dp)){
                        Text("Avisos da Koi",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        Text("Lembretes, novidades e cuidados",color=KoiColors.Muted,fontSize=12.sp)
                    }
                    val count=notices.count{!it.read}
                    if(count>0)KoiChip("$count",KoiColors.Red)else Text("→",color=KoiColors.Red)
                }
            }
            KoiAction("+ Nova conversa",{repo.newConversation(if(folder=="Todas")"Pessoal" else folder);onClose()},Modifier.fillMaxWidth(),enabled=!busy)
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),placeholder={Text("Buscar suas conversas")},singleLine=true,shape=RoundedCornerShape(20.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                (listOf("Todas")+folders).forEach{name->FilterChip(folder==name,{folder=name},label={Text(name)})}
            }
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
                if(ordered.isEmpty())item{Text(if(search.isBlank())"Seu próximo assunto começa numa nova conversa 💜" else "Não encontrei esse assunto.",color=KoiColors.Muted,modifier=Modifier.padding(16.dp))}
                items(ordered,key={it.id}){chat->
                    val accent=when(chat.folder){"Estudos"->KoiColors.Blue;"Trabalho"->KoiColors.Red;else->KoiColors.Purple}
                    KoiPanel(Modifier.fillMaxWidth().clickable(enabled=!busy){repo.selectConversation(chat.id);onClose()},accent=accent){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text((if(chat.pinned)"★ " else "")+chat.title,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(last[chat.id]?.content ?: "Uma página nova, só sua",maxLines=2,overflow=TextOverflow.Ellipsis,color=KoiColors.Muted,fontSize=12.sp)
                                Text(chat.folder+(if(chat.id==selected)" · Aberta" else ""),color=accent,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))
                            }
                            TextButton(onClick={editing=chat},enabled=!busy){Text("···")}
                        }
                    }
                }
            }
        }
    }
    editing?.let{chat->
        var title by remember(chat.id){mutableStateOf(chat.title)}
        var category by remember(chat.id){mutableStateOf(chat.folder)}
        var pinned by remember(chat.id){mutableStateOf(chat.pinned)}
        AlertDialog(onDismissRequest={editing=null},title={Text("Seu assunto, do seu jeito")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(title,{title=it.take(80)},label={Text("Nome da conversa")},singleLine=true)
            Row(Modifier.horizontalScroll(rememberScrollState())){folders.forEach{f->FilterChip(category==f,{category=f},label={Text(f)})}}
            Row(verticalAlignment=Alignment.CenterVertically){Text("Fixar no topo",Modifier.weight(1f));Switch(pinned,{pinned=it})}
        }},confirmButton={TextButton(onClick={repo.editConversation(chat,title,category,pinned);editing=null}){Text("Salvar")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancelar")}})
    }
}

@Composable private fun NoticesScreen(repo:ChatRepository,onBack:()->Unit,onConversations:()->Unit,onOpenAction:(String)->Unit){
    val notices by repo.notices.collectAsState()
    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onBack){Text("← Voltar")};TextButton(onConversations){Text("Chats")}}
        KoiPageHeading("RECADINHOS","Avisos da Koi","Pequenos cuidados com seu dia",kind="chat")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            KoiChip("${notices.count{!it.read}} não lidos",KoiColors.Red)
            Spacer(Modifier.weight(1f));TextButton({repo.readAllNotices()},enabled=notices.any{!it.read}){Text("Ler todos")}
        }
        Text("Avisos de mais de 60 dias saem aos poucos. Suas conversas ficam guardadas.",color=KoiColors.Muted,fontSize=12.sp)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(notices.isEmpty())item{KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
                Text("Tudo tranquilo por aqui 💜",fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text("Quando houver um lembrete ou novidade importante, eu deixo um recadinho aqui.",color=KoiColors.Muted)
            }}
            items(notices,key={it.id}){notice->KoiPanel(Modifier.fillMaxWidth(),accent=if(notice.read)KoiColors.Blue else KoiColors.Red){
                Eyebrow(notice.category.uppercase()+" · "+java.time.Instant.ofEpochMilli(notice.occurredAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM · HH:mm")))
                Text(notice.title,fontWeight=FontWeight.Bold,fontSize=19.sp)
                Text(notice.content,color=KoiColors.Muted)
                Row{
                    if(!notice.read)TextButton({repo.readNotice(notice.id)}){Text("Marcar como lido")}
                    TextButton({repo.readNotice(notice.id);onOpenAction(if(notice.category=="E-mail")"Ferramentas" else if(notice.category=="Tarefa")"Tarefas" else "Meu ritmo")}){Text("Conferir →")}
                }
            }}
        }
    }
}
