package com.thiago.assistentepessoal.tools

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.*

private data class PhoneContact(val name:String,val number:String)

@Composable fun ContactsMessagesPanel(){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var permitted by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED)}
    var query by remember{mutableStateOf("")}
    var contacts by remember{mutableStateOf<List<PhoneContact>>(emptyList())}
    var selected by remember{mutableStateOf<PhoneContact?>(null)}
    var body by remember{mutableStateOf("")}
    var info by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->permitted=granted;if(!granted){contacts=emptyList();selected=null;info="Você pode permitir contatos nas configurações do Android."}}
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){
            permitted=ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED
            if(!permitted){contacts=emptyList();selected=null}
        }}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    KoiDisclosure("Contatos e enviar mensagens","Escolha uma pessoa e prepare sua mensagem","chat",KoiColors.Blue){
        Text("Busca seus contatos neste celular. Nenhum contato é enviado à IA. Você confere e envia no WhatsApp ou no aplicativo de SMS.",color=KoiColors.Muted,fontSize=13.sp)
        if(!permitted)KoiAction("Permitir contatos",{permission.launch(Manifest.permission.READ_CONTACTS)})
        else {
            OutlinedTextField(query,{if(it.length<=80){query=it;contacts=emptyList()}},label={Text("Nome do contato")},modifier=Modifier.fillMaxWidth())
            KoiAction(if(busy)"Buscando…" else "Buscar contato",{scope.launch{
                busy=true;info=null
                try{contacts=withContext(Dispatchers.IO){
                    val uri=Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,Uri.encode(query.trim()))
                    context.contentResolver.query(uri,arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER),null,null,null)?.use{cursor->
                        buildList{while(cursor.moveToNext() && size<30){add(PhoneContact(cursor.getString(0).orEmpty().take(120),cursor.getString(1).orEmpty().take(60)))}}
                    }.orEmpty().distinct()
                };if(contacts.isEmpty())info="Não encontrei esse contato."}
                catch(e:Exception){if(e is CancellationException)throw e;contacts=emptyList();info="Não consegui consultar os contatos. Confira a permissão."}
                finally{busy=false}
            }},enabled=!busy && query.trim().length>=2)
            contacts.forEach{contact->TextButton(onClick={selected=contact;body=""}){Text("${contact.name} · ${contact.number}")}}
        }
        selected?.let{contact->
            Text("Para: ${contact.name} · ${contact.number}",color=KoiColors.Blue)
            OutlinedTextField(body,{if(it.length<=4000)body=it},label={Text("Sua mensagem")},minLines=2,maxLines=5,modifier=Modifier.fillMaxWidth())
            KoiAction("Revisar no SMS",{openIntent(context,Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",contact.number,null)).putExtra("sms_body",body))},enabled=body.isNotBlank())
            TextButton(onClick={
                val digits=contact.number.filter{it.isDigit()}
                val international=if(contact.number.trim().startsWith("+") || digits.length>11)digits else "55$digits"
                if(international.length in 10..15)openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$international?text=${Uri.encode(body)}")))
                else info="Confira o número com código do país antes de abrir o WhatsApp."
            },enabled=body.isNotBlank()){Text("Revisar no WhatsApp")}
            Text("Números locais usam +55. A Koi abre o rascunho; ainda não enviou a mensagem.",color=KoiColors.Muted,fontSize=12.sp)
        }
        info?.let{Text(it,color=KoiColors.Blue)}
    }
    ReceivedMessagesPanel()
}

@Composable private fun ReceivedMessagesPanel(){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("koi-device-access",0)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var enabled by remember{mutableStateOf(PhoneMessages.enabled(context))}
    var access by remember{mutableStateOf(false)}
    var allowed by remember{mutableStateOf(PhoneMessages.allowed(context))}
    val messages by PhoneMessages.items.collectAsState()
    var info by remember{mutableStateOf<String?>(null)}
    fun refreshAccess(){
        access=context.packageName in androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
        if(!access)PhoneMessages.clear()
    }
    DisposableEffect(lifecycle){
        refreshAccess()
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refreshAccess()}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    KoiDisclosure("Mensagens recebidas","Avisos atuais dos aplicativos autorizados","chat",KoiColors.Purple){
        Text("O Android dá acesso às notificações. A Koi mostra apenas os aplicativos escolhidos abaixo, sem guardar o conteúdo ou enviá-lo à IA. Não lê conversas antigas nem responde sozinha.",color=KoiColors.Muted,fontSize=13.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text("Mostrar minhas mensagens",modifier=Modifier.weight(1f))
            Switch(enabled,onCheckedChange={value->enabled=value;prefs.edit().putBoolean("messages",value).apply();PhoneMessages.clear()
                if(value && access)NotificationListenerService.requestRebind(ComponentName(context,KoiNotificationListener::class.java))
            })
        }
        if(enabled){
            listOf("WhatsApp" to "com.whatsapp","WhatsApp Business" to "com.whatsapp.w4b","Google Mensagens / SMS" to "com.google.android.apps.messaging","Mensagens Xiaomi / SMS" to "com.android.mms","Telegram" to "org.telegram.messenger","Gmail" to "com.google.android.gm").forEach{(label,pkg)->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,modifier=Modifier.weight(1f));Checkbox(pkg in allowed,{checked->
                    allowed=if(checked)allowed+pkg else allowed-pkg;prefs.edit().putStringSet("message-apps",allowed).apply();PhoneMessages.clear()
                })}
            }
            KoiAction(if(access)"Revisar acesso às notificações" else "Autorizar no Android",{
                openIntent(context,Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
            Text(if(access)"Acesso concedido. Novos avisos autorizados aparecem aqui." else "Ative “Mensagens com a Koi” na tela do Android.",color=KoiColors.Blue,fontSize=13.sp)
            if(access){
                if(messages.isEmpty())Text("Nenhuma mensagem visível no momento.",color=KoiColors.Muted)
                messages.forEach{message->KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
                    Eyebrow(message.app,KoiColors.Blue);Text(message.title,fontSize=16.sp);Text(message.text,fontSize=14.sp)
                    if(message.open!=null)TextButton(onClick={runCatching{message.open.send()}.onFailure{info="O aviso não está mais disponível. Abra o aplicativo."}}){Text("Abrir conversa no aplicativo")}
                }}
            }
        }
        info?.let{Text(it,color=KoiColors.Blue)}
    }
}
