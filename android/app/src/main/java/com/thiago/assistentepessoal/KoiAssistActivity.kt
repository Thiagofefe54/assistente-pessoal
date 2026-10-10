package com.thiago.assistentepessoal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.thiago.assistentepessoal.chat.MessageStatus
import com.thiago.assistentepessoal.tools.KoiVoiceCapture
import com.thiago.assistentepessoal.tools.rememberKoiVoice

/** User-invoked assistant surface. Does not read the foreground app or start listening automatically. */
class KoiAssistActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {KoiwaiTheme {MotionEnvironment {KoiAssistPanel(onClose={finish()},onExpand={
            startActivity(Intent(this,MainActivity::class.java).putExtra("openChat",true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        })}}}
    }
}

@Composable
fun KoiAssistPanel(onClose:()->Unit,onExpand:()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val repo by app.repositories.collectAsState()
    val history by repo.messages.collectAsState()
    val busy by repo.busy.collectAsState()
    var input by rememberSaveable{mutableStateOf("")}
    var requestId by rememberSaveable{mutableStateOf<String?>(null)}
    var capture by remember{mutableStateOf(false)}
    var info by remember{mutableStateOf<String?>(null)}
    val voice=rememberKoiVoice()
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed ->
        if(allowed)capture=true else info="Permita o microfone para falar com a Koi."
    }
    val request=history?.firstOrNull{it.id==requestId}
    val reply=history?.lastOrNull{requestId!=null && it.replyTo==requestId && it.role=="assistant"}
    LaunchedEffect(repo){requestId=null}
    LaunchedEffect(reply?.id){
        reply?.let{if(app.getSharedPreferences("koiwai-preferences",0).getBoolean("speak-replies",false))voice.speak(it.content)}
    }
    DisposableEffect(voice){onDispose{voice.stop()}}
    if(capture)KoiVoiceCapture(onText={input=it},onClose={capture=false})
    Box(Modifier.fillMaxSize()) {
        // The underlying application remains visible. Only explicit taps on the scrim dismiss.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.35f)).clickable(onClick=onClose).semantics{contentDescription="Fechar painel da Koi"})
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding(),
            shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp),color=KoiColors.Ink,shadowElevation=16.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(34.dp,4.dp).background(KoiColors.Muted.copy(alpha=.35f),RoundedCornerShape(50)).align(Alignment.CenterHorizontally))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.koiwai),null,Modifier.size(48.dp))
                    Column(Modifier.weight(1f)){Text("Estou aqui 💜",fontSize=23.sp,fontWeight=FontWeight.Bold);Text("Seu momento com a Koi",fontSize=12.sp,color=KoiColors.Muted)}
                    TextButton(onClick=onClose){Text("Fechar")}
                }
                if(reply==null && !busy)Text("Me conte o que você precisa. Pode falar do seu jeito.",fontSize=14.sp,color=KoiColors.Muted)
                if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("A Koi está respondendo…",color=KoiColors.Muted,fontSize=12.sp)}
                reply?.let {message ->
                    KoiPanel(Modifier.fillMaxWidth()){
                        Text(message.content,fontSize=16.sp,lineHeight=24.sp)
                        if(message.actionReceiptJson!=null)TextButton(onClick=onExpand){Text("Ver resultado e ações no Chat →")}
                        TextButton(onClick={voice.speak(message.content)},enabled=voice.ready){Text("Ouvir resposta")}
                    }
                }
                if(request?.status==MessageStatus.FAILED){
                    Text(com.thiago.assistentepessoal.cloud.savedConnectionMessage(request?.error),color=KoiColors.Red,fontSize=13.sp)
                    TextButton(onClick={request?.let{repo.retry(it)}},enabled=!busy){Text("Tentar novamente")}
                }
                info?.let{Text(it,color=KoiColors.Red,fontSize=12.sp)}
                if(input.length>8000)Text("Envie até 8.000 caracteres por mensagem.",color=KoiColors.Red,fontSize=12.sp)
                OutlinedTextField(input,{input=it},placeholder={Text("O que vamos fazer?")},modifier=Modifier.fillMaxWidth(),maxLines=4)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                    OutlinedButton(onClick={
                        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)capture=true
                        else microphone.launch(Manifest.permission.RECORD_AUDIO)
                    },enabled=!busy){KoiGlyph("voice");Spacer(Modifier.width(6.dp));Text("Falar")}
                    KoiAction("Enviar",{requestId=repo.send(input);input=""},Modifier.weight(1f),!busy && history!=null && input.isNotBlank() && input.length<=8000)
                }
                TextButton(onClick=onExpand,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Abrir conversa completa ↗",color=KoiColors.Blue)}
            }
        }
    }
}
