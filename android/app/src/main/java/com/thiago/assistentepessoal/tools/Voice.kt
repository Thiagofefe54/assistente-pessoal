package com.thiago.assistentepessoal.tools

import android.speech.tts.TextToSpeech
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxWidth
import java.util.Locale

class KoiVoice(context:Context){
    var ready by mutableStateOf(false)
        private set
    var info by mutableStateOf<String?>(null)
        private set
    var choices by mutableStateOf<List<android.speech.tts.Voice>>(emptyList())
        private set
    private val prefs=context.getSharedPreferences("koiwai-preferences",0)
    private var engine:TextToSpeech?=null
    private var closed=false
    init {
        engine=TextToSpeech(context.applicationContext){status->
            android.os.Handler(android.os.Looper.getMainLooper()).post{
            if(closed)return@post
            if(status==TextToSpeech.SUCCESS){
                val supported=engine?.setLanguage(Locale.forLanguageTag("pt-BR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ready=supported>=0
                choices=engine?.voices?.filter{it.locale.language=="pt" && it.locale.country=="BR"}?.sortedWith(compareBy({it.isNetworkConnectionRequired},{it.name}))?.take(12).orEmpty()
                configure(prefs.getFloat("voice-rate",1f),prefs.getFloat("voice-pitch",1f),prefs.getString("voice-name",null))
                if(!ready)info="Instale uma voz em português nas configurações de voz do Android."
            }else info="O leitor de voz não está disponível neste celular."
            }
        }
    }
    fun configure(rate:Float,pitch:Float,name:String?){
        engine?.setSpeechRate(rate.coerceIn(.7f,1.3f));engine?.setPitch(pitch.coerceIn(.8f,1.2f))
        choices.firstOrNull{it.name==name}?.let{engine?.voice=it}
    }
    fun speak(text:String){
        configure(prefs.getFloat("voice-rate",1f),prefs.getFloat("voice-pitch",1f),prefs.getString("voice-name",null))
        if(!ready){info="A voz está iniciando ou não está disponível.";return}
        speechText(text).chunked(TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1000)).forEachIndexed{index,part->
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


@Composable fun VoiceCustomizationPanel(voice:KoiVoice){
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{context.getSharedPreferences("koiwai-preferences",0)}
    var expanded by remember{mutableStateOf(false)}
    var rate by remember{mutableFloatStateOf(prefs.getFloat("voice-rate",1f))}
    var pitch by remember{mutableFloatStateOf(prefs.getFloat("voice-pitch",1f))}
    var selected by remember{mutableStateOf(prefs.getString("voice-name",null))}
    var previewPlayer by remember{mutableStateOf<android.media.MediaPlayer?>(null)}
    var previewError by remember{mutableStateOf<String?>(null)}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){previewPlayer?.release();previewPlayer=null}}
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer);previewPlayer?.release();previewPlayer=null}
    }
    com.thiago.assistentepessoal.KoiPanel(androidx.compose.ui.Modifier.fillMaxWidth()){
        androidx.compose.material3.TextButton(onClick={expanded=!expanded}){androidx.compose.material3.Text(if(expanded)"Fechar ajustes de voz" else "Ajustar a voz da Koi")}
        if(expanded){
            androidx.compose.material3.Text("Sua voz escolhida · Koi expressiva")
            androidx.compose.material3.TextButton(onClick={
                voice.stop();previewPlayer?.release();previewPlayer=null;previewError=null
                try{
                    val player=android.media.MediaPlayer.create(context,com.thiago.assistentepessoal.R.raw.koi_voice_preview)
                    if(player==null)previewError="Não consegui reproduzir a amostra." else{
                        previewPlayer=player
                        player.setOnCompletionListener{it.release();if(previewPlayer===it)previewPlayer=null}
                        player.setOnErrorListener{p,_,_->p.release();if(previewPlayer===p)previewPlayer=null;previewError="Não consegui reproduzir a amostra.";true}
                        player.start()
                    }
                }catch(e:Exception){previewError="Não consegui reproduzir a amostra."}
            }){androidx.compose.material3.Text("Ouvir Koi expressiva · amostra de 5 segundos")}
            androidx.compose.material3.TextButton(enabled=previewPlayer!=null,onClick={previewPlayer?.release();previewPlayer=null}){androidx.compose.material3.Text("Parar amostra")}
            androidx.compose.material3.Text("Amostra expressiva em português escolhida por você. Funciona sem internet. Respostas do chat ainda usam a voz Android escolhida abaixo; esta prévia não é uma voz contínua exclusiva.")
            previewError?.let{androidx.compose.material3.Text(it)}
            androidx.compose.material3.Text("Perfis da Koi · comece por Koi delicada")
            koiVoiceProfiles.forEach{profile->
                androidx.compose.material3.TextButton(enabled=voice.ready,onClick={
                    previewPlayer?.release();previewPlayer=null
                    rate=profile.rate;pitch=profile.pitch
                    prefs.edit().putFloat("voice-rate",rate).putFloat("voice-pitch",pitch).putString("voice-profile",profile.title).apply()
                    voice.configure(rate,pitch,selected);voice.speak(profile.example)
                }){androidx.compose.material3.Text("Aplicar e ouvir · ${profile.title}")}
            }
            androidx.compose.material3.Text("Perfil ajusta ritmo e tom da voz instalada. Escolha abaixo a voz que você acha mais feminina; o Android não informa gênero. Não cria voz exclusiva nem contrata serviço.")
            androidx.compose.material3.Text("Velocidade da fala")
            androidx.compose.material3.Slider(value=rate,onValueChange={rate=it;prefs.edit().putFloat("voice-rate",it).apply()},valueRange=.7f..1.3f)
            androidx.compose.material3.Text("Tom da voz")
            androidx.compose.material3.Slider(value=pitch,onValueChange={pitch=it;prefs.edit().putFloat("voice-pitch",it).apply()},valueRange=.8f..1.2f)
            voice.choices.forEachIndexed{i,v->
                androidx.compose.material3.TextButton(onClick={selected=v.name;prefs.edit().putString("voice-name",v.name).apply();voice.configure(rate,pitch,v.name)}){
                    androidx.compose.material3.Text("${if(selected==v.name)"✓ " else ""}Opção ${i+1} · ${if(v.isNetworkConnectionRequired)"usa rede" else "local"}")
                }
            }
            androidx.compose.material3.Text("Qualidade depende das vozes instaladas no Android. Ajustes não criam uma voz nova.")
            androidx.compose.material3.TextButton(onClick={previewPlayer?.release();previewPlayer=null;voice.speak("Oi, Mestre. Um passo de cada vez, estou aqui com você.")},enabled=voice.ready){androidx.compose.material3.Text("Ouvir esta voz")}
        }
    }
}
