package com.thiago.assistentepessoal.tools

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thiago.assistentepessoal.KoiColors

/** One user-started utterance; never continuously restarts or sends a chat message. */
@Composable
fun KoiVoiceCapture(onText:(String)->Unit,onClose:()->Unit){
    val context=LocalContext.current
    val currentText by rememberUpdatedState(onText)
    val currentClose by rememberUpdatedState(onClose)
    val lifecycleOwner=androidx.compose.ui.platform.LocalLifecycleOwner.current
    var preview by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("Pode falar, estou ouvindo 💜")}
    var listening by remember{mutableStateOf(false)}
    var attempt by remember{mutableIntStateOf(0)}
    val local=remember{android.os.Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}
    val recognizer=remember{runCatching{
        if(android.os.Build.VERSION.SDK_INT>=31 && local)SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else if(SpeechRecognizer.isRecognitionAvailable(context))SpeechRecognizer.createSpeechRecognizer(context) else null
    }.getOrNull()}
    DisposableEffect(recognizer,lifecycleOwner){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){recognizer?.cancel();currentClose()}
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        recognizer?.setRecognitionListener(object:RecognitionListener{
            override fun onReadyForSpeech(params:Bundle?){listening=true}
            override fun onBeginningOfSpeech(){}
            override fun onRmsChanged(rmsdB:Float){}
            override fun onBufferReceived(buffer:ByteArray?){}
            override fun onEndOfSpeech(){listening=false;status="Entendendo sua fala…"}
            override fun onError(error:Int){listening=false;status=when(error){
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"Permita o microfone nas configurações da Koi."
                SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->"Confira a conexão do serviço de voz."
                SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->"Não consegui ouvir. Quer tentar de novo?"
                else->"A voz não ficou disponível agora. Tente novamente."}}
            override fun onResults(results:Bundle?){
                listening=false
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf{it.isNotBlank()}?.let{currentText(it.take(8000));currentClose()}
            }
            override fun onPartialResults(results:Bundle?){preview=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()}
            override fun onEvent(eventType:Int,params:Bundle?){}
        })
        onDispose{lifecycleOwner.lifecycle.removeObserver(observer);recognizer?.cancel();recognizer?.destroy()}
    }
    LaunchedEffect(attempt){
        if(recognizer==null){status="Não encontrei um serviço de voz no celular.";return@LaunchedEffect}
        try{listening=true;recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true))}
        catch(e:SecurityException){listening=false;status="Permita o microfone para falar com a Koi."}
        catch(e:RuntimeException){listening=false;status="Não consegui iniciar a voz agora. Tente novamente."}
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Fale com a Koi 💜")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(status,color=KoiColors.Blue)
        if(listening)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(preview.isNotBlank())Text(preview)
        Text(if(local)"Transcrição local disponível neste Android." else "Serviço de voz do Android; pode usar rede. Preferência offline é solicitada.",style=MaterialTheme.typography.bodySmall)
        Text("Sua fala vai para o campo de mensagem. Revise antes de enviar. A transcrição usa o serviço de voz do Android.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={if(listening)recognizer?.stopListening() else {preview="";status="Pode falar 💜";attempt++}},enabled=recognizer!=null){Text(if(listening)"Terminar fala" else "Tentar novamente")}},dismissButton={TextButton(onClick=onClose){Text("Fechar")}})
}
