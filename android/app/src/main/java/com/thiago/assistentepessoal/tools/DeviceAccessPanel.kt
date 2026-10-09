package com.thiago.assistentepessoal.tools

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LaunchableApp(val label:String,val component:ComponentName)

@Composable
fun DeviceAccessPanel(){
    val context=LocalContext.current
    var phone by rememberSaveable{mutableStateOf("")}
    var search by rememberSaveable{mutableStateOf("")}
    var expanded by rememberSaveable{mutableStateOf(false)}
    val apps by produceState<List<LaunchableApp>>(emptyList(),context){
        value=withContext(Dispatchers.IO){
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                .filter{it.activityInfo.packageName!=context.packageName}
                .map{LaunchableApp(it.loadLabel(context.packageManager).toString(),ComponentName(it.activityInfo.packageName,it.activityInfo.name))}
                .distinctBy{it.component}.sortedBy{it.label.lowercase()}
        }
    }
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Red){
        Eyebrow("KOI NO SEU CELULAR",KoiColors.Red)
        Text("Seus aplicativos e acessos",fontSize=22.sp)
        Text("Você escolhe a ação. A lista de apps fica neste celular e não é enviada à IA.",fontSize=12.sp,color=KoiColors.Muted)
        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Fechar aplicativos" else "Abrir um aplicativo")}
        if(expanded){
            OutlinedTextField(search,{if(it.length<=80)search=it},label={Text("Buscar aplicativo")},modifier=Modifier.fillMaxWidth())
            val matches=apps.filter{it.label.contains(search,ignoreCase=true)}
            if(matches.isEmpty())Text("Nenhum aplicativo encontrado.",color=KoiColors.Muted)
            matches.take(12).forEach{app->TextButton(onClick={openIntent(context,Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component))}){Text(app.label)}}
            if(matches.size>12)Text("Digite o nome para encontrar mais apps.",fontSize=12.sp,color=KoiColors.Muted)
        }
        OutlinedTextField(phone,{input->if(input.length<=40)phone=input.filter{it.isDigit() || it in "+ ()-"}},label={Text("Telefone")},modifier=Modifier.fillMaxWidth())
        KoiAction("Abrir discador",{openIntent(context,Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone,null)))},enabled=phone.any{it.isDigit()})
        Text("Confira o número e toque em ligar no discador.",fontSize=12.sp,color=KoiColors.Muted)
        TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,android.provider.ContactsContract.Contacts.CONTENT_URI))}){Text("Abrir contatos")}
        TextButton(onClick={openIntent(context,Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))}){Text("Abrir alarmes")}
        TextButton(onClick={openIntent(context,Intent(Settings.ACTION_WIFI_SETTINGS))}){Text("Abrir Wi-Fi")}
        TextButton(onClick={openIntent(context,Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}){Text("Gerenciar permissões da Koi")}
        Text("Câmera, voz e arquivos usam as telas do Android. Leitura de notificações e controle de outras telas ainda não estão integrados.",fontSize=12.sp,color=KoiColors.Muted)
    }
}

@Composable
fun ConnectionsPanel(){
    val context=LocalContext.current
    CalendarConnectionPanel()
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Eyebrow("CONEXÕES",KoiColors.Blue)
        Text("Seu mundo com a Koi",fontSize=22.sp)
        Text("Estes atalhos abrem os serviços. Eles não conectam sua conta à Koi nem dão acesso aos seus dados.",fontSize=12.sp,color=KoiColors.Muted)
        listOf("Google Agenda" to "https://calendar.google.com/", "Google Tasks" to "https://tasks.google.com/", "Gmail" to "https://mail.google.com/", "Google Drive" to "https://drive.google.com/", "ChatGPT" to "https://chatgpt.com/").forEach{(name,url)->
            TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse(url)))}){Text("Abrir $name")}
        }
        Text("Agenda sincronizada já pode ser consultada acima; Tasks, Gmail e Drive precisam de OAuth próprio do projeto. Open Finance ainda não está conectado. O Plus não inclui créditos de API. Integração bancária começará por consulta; pagamentos não estão disponíveis.",fontSize=12.sp,color=KoiColors.Muted)
    }
}
