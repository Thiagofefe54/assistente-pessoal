package com.thiago.assistentepessoal.tools

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONObject

/** Fixed capability list: model text never becomes arbitrary Android code or a URI. */
fun executeKoiDeviceAction(context:Context,raw:String):String {
    return try {
        val data=JSONObject(raw)
        require(data.getString("tool")=="device")
        val action=data.getString("action")
        val value=data.getString("value").trim()
        require(value.length<=300)
        val intent=when(action){
            "open_app"->{
                require(value.isNotBlank())
                val apps=context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                    .filter{it.activityInfo.packageName!=context.packageName}
                val exact=apps.filter{it.loadLabel(context.packageManager).toString().equals(value,true)}
                val matches=(exact.ifEmpty{apps.filter{it.loadLabel(context.packageManager).toString().contains(value,true)}})
                    .distinctBy{it.activityInfo.packageName}
                if(matches.isEmpty())return "Não encontrei esse aplicativo. Confira em Ferramentas → Celular."
                if(matches.size!=1)return "Encontrei mais de um aplicativo. Diga o nome completo para escolher."
                val app=matches.single().activityInfo
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(app.packageName,app.name))
            }
            "play_music"->Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").putExtra("query",value)
            "open_settings"->Intent(when(value){"wifi"->Settings.ACTION_WIFI_SETTINGS;"bluetooth"->Settings.ACTION_BLUETOOTH_SETTINGS;"app"->Settings.ACTION_APPLICATION_DETAILS_SETTINGS;else->return "Esse ajuste não está disponível."})
                .apply{if(value=="app")this.data=Uri.parse("package:${context.packageName}")}
            "timer"->{val seconds=value.toIntOrNull() ?: return "O tempo não foi entendido.";require(seconds in 1..86400)
                Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,seconds).putExtra(AlarmClock.EXTRA_MESSAGE,"Koiwai").putExtra(AlarmClock.EXTRA_SKIP_UI,false)}
            "alarm"->{require(value.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d")));val parts=value.split(':')
                Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,parts[0].toInt()).putExtra(AlarmClock.EXTRA_MINUTES,parts[1].toInt()).putExtra(AlarmClock.EXTRA_MESSAGE,"Koiwai").putExtra(AlarmClock.EXTRA_SKIP_UI,false)}
            "navigate"->{require(value.isNotBlank());Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(value)))}
            "search_web"->{require(value.isNotBlank());Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(value)))}
            else->return "Essa ferramenta ainda não está disponível."
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Pedido entregue ao aplicativo. Confira o resultado na tela que abriu."
    }catch(e:android.content.ActivityNotFoundException){"Nenhum aplicativo compatível com essa ação foi encontrado."}
    catch(e:SecurityException){"O Android bloqueou essa ação. Confira as permissões do aplicativo."}
    catch(e:IllegalArgumentException){"Os dados dessa ação não são válidos. Peça novamente à Koi."}
    catch(e:org.json.JSONException){"Não consegui ler essa ação. Peça novamente à Koi."}
}
