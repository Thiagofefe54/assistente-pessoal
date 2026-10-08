package com.thiago.assistentepessoal.tools

import android.speech.tts.TextToSpeech
import android.content.Context
import androidx.compose.runtime.*
import java.util.Locale

class KoiVoice(context:Context){
    var ready by mutableStateOf(false)
        private set
    var info by mutableStateOf<String?>(null)
        private set
    private var engine:TextToSpeech?=null
    private var closed=false
    init {
        engine=TextToSpeech(context.applicationContext){status->
            android.os.Handler(android.os.Looper.getMainLooper()).post{
            if(closed)return@post
            if(status==TextToSpeech.SUCCESS){
                val supported=engine?.setLanguage(Locale.forLanguageTag("pt-BR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ready=supported>=0
                if(!ready)info="Instale uma voz em português nas configurações de voz do Android."
            }else info="O leitor de voz não está disponível neste celular."
            }
        }
    }
    fun speak(text:String){
        if(!ready){info="A voz está iniciando ou não está disponível.";return}
        text.chunked(TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1000)).forEachIndexed{index,part->
            if(engine?.speak(part,if(index==0)TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,"koi-$index")==TextToSpeech.ERROR)info="Não consegui reproduzir a voz. Confira a voz instalada no Android."
        }
    }
    fun stop(){engine?.stop()}
    fun close(){closed=true;ready=false;stop();engine?.shutdown()}
}

@Composable fun rememberKoiVoice():KoiVoice{
    val context=androidx.compose.ui.platform.LocalContext.current
    val voice=remember(context){KoiVoice(context)}
    DisposableEffect(voice){onDispose{voice.close()}}
    val owner=context as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(voice,owner){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)voice.stop()}
        owner?.lifecycle?.addObserver(observer)
        onDispose{owner?.lifecycle?.removeObserver(observer)}
    }
    return voice
}
