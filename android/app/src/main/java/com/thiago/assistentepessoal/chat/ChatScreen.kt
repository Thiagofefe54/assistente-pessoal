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

@Composable
fun ChatScreen(onBack: () -> Unit, onAccount: () -> Unit) {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val repository by app.repositories.collectAsState()
    val account by app.auth.account.collectAsState()
    val history by repository.messages.collectAsState()
    val busy by repository.busy.collectAsState()
    val error by repository.error.collectAsState()
    val messages=history.orEmpty()
    var input by rememberSaveable {mutableStateOf("")}
    var pendingId by rememberSaveable {mutableStateOf<String?>(null)}
    var pendingText by rememberSaveable {mutableStateOf("")}
    val listState=rememberLazyListState()
    val motion=LocalKoiMotion.current
    LaunchedEffect(messages) {
        if(pendingId!=null && messages.any{it.id==pendingId}) {
            if(input==pendingText) input=""
            pendingId=null
        }
    }
    LaunchedEffect(messages.lastOrNull()?.id) {
        if(messages.isNotEmpty()) {
            val headers=messages.indices.count{it==0 || messages[it-1].localDate!=messages[it].localDate}
            if(motion) listState.animateScrollToItem(messages.lastIndex+headers) else listState.scrollToItem(messages.lastIndex+headers)
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=18.dp,vertical=10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            IconButton(onClick=onBack,modifier=Modifier.semantics{contentDescription="Voltar"}) {KoiGlyph("back",Color.White)}
            Image(painterResource(R.drawable.koiwai),null,Modifier.size(46.dp).clip(CircleShape).background(KoiColors.Purple.copy(alpha=.15f)).border(1.dp,KoiColors.Purple.copy(alpha=.5f),CircleShape))
            Column(Modifier.weight(1f)) {
                Text("Koiwai",fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text(if(busy && history!=null) "Enviando…" else "Seu espaço com a Koi",color=KoiColors.Muted,fontSize=11.sp)
            }
            TextButton(onClick=onAccount,enabled=!busy) {Text("Conta",fontSize=12.sp)}
        }
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Eyebrow("CONVERSA")
            KoiChip(if(account==null) "Neste celular" else "Conta conectada",KoiColors.Blue)
        }
        HorizontalDivider(color=KoiColors.Purple.copy(alpha=.16f))
        LazyColumn(state=listState,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(history==null) item {Text("Carregando conversa…",color=KoiColors.Muted)}
            else if(messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    OrbitEmblem("spark",KoiColors.Purple,Modifier.size(100.dp))
                    Text("Vamos conversar?",fontSize=26.sp,fontWeight=FontWeight.Bold)
                    Text("Um pensamento, uma ideia, seu dia.\nEste espaço é seu.",color=KoiColors.Muted,fontSize=14.sp)
                    KoiChip("Respostas de teste nesta versão",KoiColors.Blue)
                }
            }
            messages.forEachIndexed { index,message ->
                if(index==0 || messages[index-1].localDate!=message.localDate) item(key="day-${message.id}") {
                    Box(Modifier.fillMaxWidth().padding(vertical=4.dp),contentAlignment=Alignment.Center){KoiChip(dayLabel(message.localDate),KoiColors.Muted)}
                }
                item(key=message.id) { MessageBubble(message,busy){repository.retry(message)} }
            }
        }
        if(busy && history!=null) ProcessingIndicator()
        if(input.length>8000) Text("Envie até 8.000 caracteres por mensagem.",color=KoiColors.Red,fontSize=12.sp)
        error?.let {Text(it,color=KoiColors.Red,fontSize=12.sp,modifier=Modifier.padding(bottom=8.dp))}
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(KoiColors.Card.copy(alpha=.96f)).border(1.dp,KoiColors.Purple.copy(alpha=.35f),RoundedCornerShape(22.dp)).padding(6.dp),verticalAlignment=Alignment.CenterVertically) {
            TextField(value=input,onValueChange={input=it},placeholder={Text("Digite uma mensagem…",fontSize=14.sp)},
                modifier=Modifier.weight(1f),maxLines=4,
                colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
                    focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,cursorColor=KoiColors.Purple))
            KoiAction(if(busy) "…" else "Enviar",{pendingText=input;pendingId=repository.send(input)},
                enabled=!busy && history!=null && input.isNotBlank() && input.length<=8000)
        }
    }
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
    val shape=if(user) RoundedCornerShape(20.dp,20.dp,5.dp,20.dp) else RoundedCornerShape(5.dp,20.dp,20.dp,20.dp)
    Box(Modifier.fillMaxWidth(),contentAlignment=if(user) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max=320.dp).clip(shape)
            .background(Brush.linearGradient(if(user) listOf(Color(0xFF54328E),Color(0xFF34285C)) else listOf(Color(0xFF201B32),Color(0xFF17182B))))
            .border(1.dp,(if(user) KoiColors.Purple else KoiColors.Blue).copy(alpha=.23f),shape).padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(if(user) "VOCÊ" else "✦ KOIWAI",color=if(user) Color(0xFFD9C4FF) else Color(0xFF9AAFFF),fontSize=10.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
            SelectionContainer {Text(message.content,color=Color.White,fontSize=15.sp,lineHeight=23.sp)}
            val status=when(message.status){MessageStatus.SENDING->" • Enviando…";MessageStatus.FAILED->" • Falha no envio";else->""}
            Text(messageTime(message)+status,color=KoiColors.Muted,fontSize=10.sp,modifier=Modifier.align(Alignment.End))
            if(user && message.status==MessageStatus.FAILED) {
                Text(message.error ?: "Envio não concluído.",color=Color(0xFFFFB4C3),fontSize=12.sp)
                TextButton(onClick=onRetry,enabled=!busy){Text("Tentar novamente",color=Color.White)}
            }
        }
    }
}
