package com.thiago.assistentepessoal.tools

/** Presets adjust the installed engine, without impersonation or paid synthesis. */
data class KoiVoiceProfile(val title:String,val rate:Float,val pitch:Float,val example:String)
internal val koiVoiceProfiles=listOf(
    KoiVoiceProfile("Koi delicada",.94f,1.10f,"Oi, Mestre… estou aqui. Vamos cuidar do seu dia, um passinho de cada vez?"),
    KoiVoiceProfile("Koi animada",1.02f,1.08f,"Mestre, mais uma missão concluída! Vamos comemorar essa conquista e escolher o próximo passo?"),
    KoiVoiceProfile("Koi tranquila",.88f,1.02f,"Vamos respirar um pouquinho, Mestre. Você pode ir com calma. Eu te ajudo a organizar o que vem agora.")
)

internal fun speechText(text:String):String=text
    .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"),"$1")
    .replace(Regex("https?://\\S+"),"link disponível na tela")
    .replace(Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]"),"")
    .replace("**","").replace("`","").trim()
