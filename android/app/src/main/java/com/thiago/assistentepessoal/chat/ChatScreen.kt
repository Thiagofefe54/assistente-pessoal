package com.thiago.assistentepessoal.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.R
import kotlin.math.sin
import com.thiago.assistentepessoal.routine.TaskEditor
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import com.thiago.assistentepessoal.tools.*
import kotlinx.coroutines.*
import androidx.compose.ui.graphics.asImageBitmap

@Composable
internal fun ChatConversationScreen(onBack: () -> Unit, onAccount: () -> Unit, onJournal: (String) -> Unit = {},onTools:()->Unit={},onOpenAction:(String)->Unit={},onConversations:()->Unit={},imageState:MutableState<String?>) {
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val repository by app.repositories.collectAsState()
    val account by app.auth.account.collectAsState()
    val history by repository.messages.collectAsState()
    val busy by repository.busy.collectAsState()
    val error by repository.error.collectAsState()
    val tasksRepo by app.tasks.collectAsState()
    var reviewing by remember(repository) {mutableStateOf<ChatMessage?>(null)}
    val selected by repository.conversationId.collectAsState()
    val conversations by repository.conversations.collectAsState()
    val messages=history.orEmpty().filter{it.conversationId==selected}
    val openedAt=remember(repository){System.currentTimeMillis()}
    var input by rememberSaveable {mutableStateOf("")}
    var image by imageState
    var mediaInfo by remember{mutableStateOf<String?>(null)}
    val mediaScope=rememberCoroutineScope()
    val voice=rememberKoiVoice()
    var voiceCapture by remember{mutableStateOf(false)}
    var extras by remember {mutableStateOf(false)}
    var screenConsent by remember{mutableStateOf(false)}
    val sharedScreen by KoiSharedScreen.pending.collectAsState()
    LaunchedEffect(sharedScreen?.id,account?.id){
        sharedScreen?.let{capture->
            if(capture.owner==account?.id){
                if(capture.jpeg!=null){image=capture.jpeg;mediaInfo="Tela pronta. Confira a imagem e toque em Enviar para a IA interpretar. Pode consumir pontos de IA."}
                else mediaInfo=capture.error
            }
            KoiSharedScreen.clear(capture.id)
        }
    }
    if(screenConsent)AlertDialog(onDismissRequest={screenConsent=false},title={Text("Mostrar minha tela")},
        text={Text("Depois de autorizar no Android, abra a tela desejada. A Koi fará uma única captura em 7 segundos e encerrará o acesso. Volte ao Chat para conferir a imagem antes de enviar à IA. Telas protegidas podem ficar vazias.")},
        confirmButton={TextButton(onClick={screenConsent=false;openIntent(context,Intent(context,KoiScreenCaptureActivity::class.java))}){Text("Escolher tela")}},
        dismissButton={TextButton(onClick={screenConsent=false}){Text("Cancelar")}})
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted ->
        if(granted)voiceCapture=true else mediaInfo="Permita o microfone para falar com a Koi."
    }
    if(voiceCapture)KoiVoiceCapture(onText={input=it},onClose={voiceCapture=false})
    val imageLauncher=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)mediaScope.launch{
        try{image=withContext(Dispatchers.IO){imageForKoi(context,uri)};mediaInfo="Imagem pronta. Toque em Enviar para mandar à IA. A imagem fica guardada neste celular."}
        catch(e:Exception){if(e is CancellationException)throw e;mediaInfo=e.message ?: "Não consegui abrir a imagem."}
    }}
    val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->if(bitmap!=null)mediaScope.launch{
        try{image=withContext(Dispatchers.IO){jpegForKoi(bitmap)};mediaInfo="Foto pronta. Toque em Enviar para a Koi analisar."}
        catch(e:Exception){if(e is CancellationException)throw e;mediaInfo="Não consegui preparar a foto."}
        finally{bitmap.recycle()}
    }}
    var pendingId by rememberSaveable {mutableStateOf<String?>(null)}
    var pendingText by rememberSaveable {mutableStateOf("")}
    val listState=rememberLazyListState()
    val motion=LocalKoiMotion.current
    LaunchedEffect(messages) {
        if(pendingId!=null && messages.any{it.id==pendingId}) {
            if(input==pendingText) input=""
            pendingId=null
            image=null
        }
    }
    LaunchedEffect(messages.lastOrNull()?.id) {
        if(messages.isNotEmpty()) {
            val headers=messages.indices.count{it==0 || messages[it-1].localDate!=messages[it].localDate}
            if(motion) listState.animateScrollToItem(messages.lastIndex+headers) else listState.scrollToItem(messages.lastIndex+headers)
        }
    }
    var observedReply by remember(repository){mutableStateOf<String?>(null)}
    var voiceHistoryLoaded by remember(repository){mutableStateOf(false)}
    LaunchedEffect(history!=null,messages.lastOrNull{it.role=="assistant"}?.id){
        if(history==null)return@LaunchedEffect
        val last=messages.lastOrNull{it.role=="assistant"}
        val foreground=(context as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)==true
        if(voiceHistoryLoaded && last!=null && last.id!=observedReply && foreground && context.getSharedPreferences("koiwai-preferences",0).getBoolean("speak-replies",false))voice.speak(last.content)
        observedReply=last?.id;voiceHistoryLoaded=true
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=18.dp,vertical=10.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(listOf(KoiColors.Purple.copy(alpha=.12f),KoiColors.Blue.copy(alpha=.05f)))).padding(horizontal=4.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            IconButton(onClick=onBack,modifier=Modifier.semantics{contentDescription="Voltar"}) {KoiGlyph("back",Color.White)}
            Image(painterResource(R.drawable.koiwai),null,Modifier.size(46.dp).clip(CircleShape).background(KoiColors.Purple.copy(alpha=.15f)).border(1.dp,KoiColors.Purple.copy(alpha=.5f),CircleShape))
            Column(Modifier.weight(1f)) {
                Text(conversations.firstOrNull{it.id==selected}?.title ?: "Nova conversa",fontSize=19.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(if(busy && history!=null) "Preparando sua resposta…" else "Aqui, um passo de cada vez",color=KoiColors.Muted,fontSize=11.sp)
            }
            TextButton(onClick=onConversations,enabled=!busy) {Text("Chats",fontSize=12.sp)}
        }
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Eyebrow(conversations.firstOrNull{it.id==selected}?.folder?.uppercase() ?: "CONVERSA")
            KoiChip(if(account==null) "Neste celular" else "Conta conectada",KoiColors.Blue)
        }
        HorizontalDivider(color=KoiColors.Purple.copy(alpha=.16f))
        LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(history==null) item {Text("Carregando conversa…",color=KoiColors.Muted)}
            else if(messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(150.dp),contentAlignment=Alignment.Center){
                        OrbitEmblem("spark",KoiColors.Purple,Modifier.fillMaxSize())
                        Image(painterResource(R.drawable.koiwai),"Koi esperando sua mensagem",Modifier.size(128.dp))
                    }
                    Text("Pode chegar mais perto 💜",fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Text("Me conte seu dia. Vamos pensar juntos\nno próximo passo?",color=KoiColors.Muted,fontSize=14.sp)
                    listOf("Quero organizar meu dia","Quero te contar uma coisa").forEach{suggestion->OutlinedButton(onClick={input=suggestion}){Text(suggestion)}}
                }
            }
            messages.forEachIndexed { index,message ->
                if(index==0 || messages[index-1].localDate!=message.localDate) item(key="day-${message.id}") {
                    Box(Modifier.fillMaxWidth().padding(vertical=4.dp),contentAlignment=Alignment.Center){KoiChip(dayLabel(message.localDate),KoiColors.Muted)}
                }
                item(key=message.id) {
                    Column {
                        MessageBubble(message,busy){repository.retry(message)}
                        if(message.role=="assistant")Row{
                            TextButton(onClick={voice.speak(message.content)},enabled=voice.ready){Text("Ouvir",fontSize=11.sp)}
                            Box {
                                var actions by remember {mutableStateOf(false)}
                                TextButton(onClick={actions=true}){Text("Mais ···",fontSize=11.sp)}
                                DropdownMenu(actions,{actions=false}){
                                    DropdownMenuItem(text={Text("Compartilhar resposta")},onClick={actions=false;shareText(context,"Koiwai",message.content)})
                                }
                            }
                        }
                        if(message.role=="assistant"){
                            val urls=remember(message.content){Regex("https://[^\\s<>]+") .findAll(message.content).map{it.value.trimEnd('.',',',')',']')}.distinct().take(6).toList()}
                            if(urls.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState())){urls.forEachIndexed{index,url->TextButton(onClick={com.thiago.assistentepessoal.tools.openIntent(context,Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)))}){Text("Abrir fonte ${index+1}")}}}
                        }
                        message.actionReceiptJson?.takeIf{org.json.JSONObject(it).optString("tool")=="device"}?.let{raw ->
                            var outcome by remember(message.id){mutableStateOf<String?>(null)}
                            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
                                Text("Ação no seu celular",color=KoiColors.Blue)
                                TextButton(onClick={outcome=executeKoiDeviceAction(context,raw)}){Text("Abrir ação preparada →")}
                                outcome?.let{Text(it,fontSize=12.sp,color=KoiColors.Muted)}
                            }
                        }
                        message.actionReceiptJson?.takeIf{org.json.JSONObject(it).optString("tool")=="bank"}?.let{raw->
                            val query=org.json.JSONObject(raw).optString("query").takeIf{it in setOf("balance","plan")} ?: "balance"
                            BankConnectionPanel(queryMode=query,autoRead=message.id==messages.lastOrNull()?.id && message.occurredAt>=openedAt)
                        }
                        message.actionReceiptJson?.takeIf{org.json.JSONObject(it).optString("tool")=="google"}?.let{raw->
                            if(org.json.JSONObject(raw).optString("type")=="read")GoogleChatCard(raw,autoRead=message.id==messages.lastOrNull()?.id && message.occurredAt>=openedAt)
                            else Text("Ação confirmada no Google · confira em Conexões",fontSize=12.sp,color=KoiColors.Blue)
                        }
                        message.actionReceiptJson?.takeIf{org.json.JSONObject(it).optString("tool") !in setOf("device","bank","google")}?.let{raw->val receipt=org.json.JSONObject(raw)
                            TextButton(onClick={onOpenAction(if(receipt.optString("tool")=="personal")if(receipt.optString("target_kind")=="memory")"Lembranças" else when(receipt.optString("record_kind")){"list"->"Listas";"goal"->"Metas";"workout"->"Treinos";"expense","income"->"Finanças";"bill"->"Contas";"budget"->"Orçamento";"diary"->"Diário";else->"Notas"} else "Tarefas")}){Text("Ver resultado salvo →")}
                        }
                        if(message.actionReceiptJson!=null && org.json.JSONObject(message.actionReceiptJson).optString("tool") !in setOf("device","bank","google") && org.json.JSONObject(message.actionReceiptJson).optString("type")!="undo") TextButton(onClick={repository.undoAction(message)},enabled=!busy) {
                            Text("↶ Desfazer ação",color=KoiColors.Blue)
                        }
                        if(message.role=="assistant" && message.taskDraftJson!=null && tasksRepo!=null) {
                            Row {
                                TextButton(onClick={tasksRepo?.clearInfo();reviewing=message}) {Text("✦ Revisar tarefa",color=KoiColors.Purple)}
                                TextButton(onClick={repository.dismissTaskDraft(message.id)}) {Text("Descartar",color=KoiColors.Muted)}
                            }
                        }
                    }
                }
            }
        }
        if(busy && history!=null) ProcessingIndicator()
        if(input.length>8000) Text("Envie até 8.000 caracteres por mensagem.",color=KoiColors.Red,fontSize=12.sp)
        error?.let {Text(it,color=KoiColors.Red,fontSize=12.sp,modifier=Modifier.padding(bottom=8.dp))}
        (mediaInfo ?: voice.info)?.let{Text(it,color=KoiColors.Muted,fontSize=11.sp,maxLines=3)}
        image?.let{encoded->Row(verticalAlignment=Alignment.CenterVertically){
            val preview=remember(encoded){runCatching{val bytes=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()}.getOrNull()}
            preview?.let{Image(it,"Imagem escolhida",Modifier.size(60.dp))}
            TextButton(onClick={image=null;mediaInfo=null}){Text("Remover imagem")}
        }}
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KoiColors.Card.copy(alpha=.96f)).border(1.dp,KoiColors.Purple.copy(alpha=.35f),RoundedCornerShape(22.dp)).padding(6.dp),verticalAlignment=Alignment.CenterVertically) {
            Box {
                IconButton(onClick={extras=true},enabled=!busy,modifier=Modifier.semantics{contentDescription="Mais opções da conversa"}){Text("＋",fontSize=24.sp,color=KoiColors.Blue)}
                DropdownMenu(expanded=extras,onDismissRequest={extras=false}) {
                    DropdownMenuItem(text={Text("Escolher imagem")},onClick={extras=false;if(account!=null)imageLauncher.launch("image/*") else mediaInfo="Entre na sua conta para analisar imagens."})
                    DropdownMenuItem(text={Text("Mostrar minha tela")},onClick={extras=false;if(account!=null)screenConsent=true else mediaInfo="Entre na sua conta para interpretar a tela."})
                    DropdownMenuItem(text={Text("Tirar foto")},onClick={extras=false;try{if(account!=null)cameraLauncher.launch(null) else mediaInfo="Entre na sua conta para analisar fotos."}catch(_:android.content.ActivityNotFoundException){mediaInfo="Não há aplicativo de câmera disponível."}})
                    DropdownMenuItem(text={Text("Ferramentas")},onClick={extras=false;onTools()})
                    messages.lastOrNull{it.role=="user" && it.status==MessageStatus.SENT}?.let{last ->
                        if(account!=null)DropdownMenuItem(text={Text("Resumo e lembranças")},onClick={extras=false;onJournal(last.localDate)})
                    }
                    DropdownMenuItem(text={Text("Parar voz")},onClick={extras=false;voice.stop()})
                }
            }
            TextField(value=input,onValueChange={input=it},placeholder={Text("Digite uma mensagem…",fontSize=14.sp)},
                modifier=Modifier.weight(1f),maxLines=4,
                colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
                    focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=KoiColors.Purple))
            IconButton(onClick={
                if(androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED)voiceCapture=true
                else microphone.launch(android.Manifest.permission.RECORD_AUDIO)
            },enabled=!busy,modifier=Modifier.semantics{contentDescription="Falar com a Koi"}){KoiGlyph("voice",KoiColors.Purple)}
            KoiAction(if(busy) "…" else "Enviar",{pendingText=input;pendingId=repository.send(input.ifBlank{"Analise esta imagem, Koi."},image)},
                enabled=!busy && history!=null && (input.isNotBlank() || image!=null) && input.length<=8000)
        }
    }
    reviewing?.let { message -> tasksRepo?.let { tasks ->
        val saving by tasks.busy.collectAsState()
        val info by tasks.info.collectAsState()
        val initial=taskDraftFromJson(message.taskDraftJson)
        if(initial!=null) key(message.id) {
            TaskEditor(existing=null,onDismiss={if(!saving)reviewing=null},initial=initial,saving=saving,info=info) { draft ->
                val id=UUID.nameUUIDFromBytes(("koi-task:"+message.id).toByteArray(Charsets.UTF_8)).toString()
                tasks.save(draft,creationId=id,onSaved={repository.dismissTaskDraft(message.id);reviewing=null})
            }
        }
    } }
}

@Composable
private fun ProcessingIndicator() {
    val phase=motionPhase(1800)
    Row(Modifier.padding(bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Canvas(Modifier.size(width=38.dp,height=18.dp)) {
            repeat(3) { i ->
                val amount=(sin(phase.value*6.283f-i*.8f)+1f)/2f
                drawCircle(KoiColors.Purple.copy(alpha=.3f+amount*.7f),2.8.dp.toPx(),androidx.compose.ui.geometry.Offset((6+i*11).dp.toPx(),9.dp.toPx()-amount*3.dp.toPx()))
            }
        }
        Text("Enviando para a Koi…",fontSize=12.sp,color=KoiColors.Muted)
    }
}

@Composable
private fun MessageBubble(message:ChatMessage,busy:Boolean,onRetry:()->Unit) {
    val user=message.role=="user"
    val shape=if(user) RoundedCornerShape(24.dp,24.dp,8.dp,24.dp) else RoundedCornerShape(8.dp,24.dp,24.dp,24.dp)
    Box(Modifier.fillMaxWidth(),contentAlignment=if(user) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max=320.dp).clip(shape)
            .background(Brush.linearGradient(if(user) listOf(Color(0xFF623BB1),Color(0xFF354887)) else listOf(Color(0xFF1C2438),Color(0xFF151B2C))))
            .border(1.dp,(if(user) KoiColors.Purple else KoiColors.Blue).copy(alpha=.23f),shape).padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(if(user) "VOCÊ" else "✦ KOIWAI",color=if(user) Color(0xFFD9C4FF) else Color(0xFF9AAFFF),fontSize=10.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
            message.imageJpegBase64?.let{encoded->
                val preview=remember(encoded){runCatching{val data=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);android.graphics.BitmapFactory.decodeByteArray(data,0,data.size)?.asImageBitmap()}.getOrNull()}
                preview?.let{Image(it,"Imagem enviada",Modifier.fillMaxWidth().heightIn(max=180.dp))}
                Text("Imagem guardada neste celular",fontSize=10.sp,color=KoiColors.Muted)
            }
            SelectionContainer {Text(message.content,color=Color.White,fontSize=16.sp,lineHeight=25.sp)}
            val status=when(message.status){MessageStatus.SENDING->" • Enviando…";MessageStatus.FAILED->" • Falha no envio";else->""}
            Text(messageTime(message)+status,color=KoiColors.Muted,fontSize=10.sp,modifier=Modifier.align(Alignment.End))
            if(user && message.status==MessageStatus.FAILED) {
                Text(com.thiago.assistentepessoal.cloud.savedConnectionMessage(message.error),color=Color(0xFFFFB4C3),fontSize=12.sp)
                TextButton(onClick=onRetry,enabled=!busy){Text("Tentar novamente",color=Color.White)}
            }
        }
    }
}
