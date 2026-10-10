package com.thiago.assistentepessoal.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@Composable fun GoogleMailPanel(status:JSONObject?){
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val owner=account?.id
    val prefs=remember{context.getSharedPreferences("koi-mail-pending",0)}
    var pending by remember(owner){mutableStateOf(owner?.let{prefs.getString(it,null)})}
    var selected by remember(owner){mutableStateOf<JSONObject?>(null)}
    var recipient by remember(owner){mutableStateOf("")}
    var subject by remember(owner){mutableStateOf("")}
    var text by remember(owner){mutableStateOf("")}
    var review by remember(owner){mutableStateOf(false)}
    var busy by remember(owner){mutableStateOf(false)}
    var info by remember(owner){mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun clearPending(){if(owner!=null)prefs.edit().remove(owner).commit();pending=null}
    fun check(){if(owner==null || pending==null || busy)return
        busy=true;scope.launch{try{
            val result=assistantRequest(app,owner,"google-mail-status",JSONObject().put("request_id",pending),allowCached=false)
            if(app.auth.account.value?.id==owner){info=result.optString("reply");if(!result.optBoolean("uncertain")){clearPending();if(result.optBoolean("sent")){recipient="";subject="";text=""}}}
        }catch(e:Exception){if(e is CancellationException)throw e;info=e.message ?: "Não consegui conferir. Não repita o envio ainda."}
        finally{busy=false}}}
    KoiDisclosure("Escrever e-mail","Revise antes de enviar pelo Gmail","chat",KoiColors.Blue){
        Text("Sem IA e sem anexos. Destinatário, assunto e texto passam pelo servidor da Koi somente para o Gmail enviar. Não entram no histórico do chat.",fontSize=12.sp,color=KoiColors.Muted)
        if(owner==null)Text("Entre na conta Koiwai.")
        pending?.let{
            Text("Há um pedido de envio para conferir. Não vamos repetir automaticamente.",color=KoiColors.Red)
            KoiAction("Conferir último envio",{check()},enabled=!busy)
            TextButton(onClick={openIntent(context,android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://mail.google.com/mail/u/0/#sent")))}){Text("Conferir Enviados no Gmail")}
            TextButton(onClick={review=true},enabled=!busy){Text("Já conferi; preparar outro envio")}
        }
        if(pending==null){
            val rows=status?.optJSONArray("accounts")
            if(rows!=null)for(i in 0 until rows.length()){
                val row=rows.getJSONObject(i)
                TextButton(onClick={selected=row},enabled=!busy){Text((if(selected?.optString("id")==row.optString("id"))"✓ " else "")+row.getString("email"))}
            }
            val services=selected?.optJSONArray("services")
            val maySend=services!=null && (0 until services.length()).any{services.getString(it)=="mail_send"}
            if(selected!=null && !maySend)Text("Reautorize essa conta no painel Google para permitir envio de e-mails.",color=KoiColors.Blue,fontSize=13.sp)
            OutlinedTextField(recipient,{recipient=it.take(254)},label={Text("E-mail do destinatário")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
            OutlinedTextField(subject,{subject=it.take(160)},label={Text("Assunto")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
            OutlinedTextField(text,{text=it.take(10000)},label={Text("Mensagem")},minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth(),enabled=!busy)
            KoiAction("Revisar envio",{review=true},enabled=owner!=null && maySend && !busy && android.util.Patterns.EMAIL_ADDRESS.matcher(recipient.trim()).matches() && subject.isNotBlank() && text.isNotBlank())
        }
        info?.let{Text(it,color=KoiColors.Blue,fontSize=13.sp)}
    }
    if(review && pending!=null)AlertDialog(onDismissRequest={review=false},title={Text("Preparar outro envio?")},
        text={Text("Use somente depois de conferir Enviados no Gmail. Se a primeira mensagem foi enviada, uma nova pode ser duplicada.")},
        confirmButton={TextButton(onClick={review=false;clearPending()}){Text("Conferi; continuar")}},dismissButton={TextButton(onClick={review=false}){Text("Voltar")}})
    else if(review)AlertDialog(onDismissRequest={review=false},title={Text("Enviar este e-mail?")},
        text={Column{Text("De: ${selected?.optString("email")}");Text("Para: ${recipient.trim()}");Text("Assunto: $subject");Text(text.take(1500));if(text.length>1500)Text("Prévia abreviada; revise o texto completo no formulário.")}},
        confirmButton={TextButton(onClick={
            review=false
            if(owner!=null && selected!=null && pending==null && !busy){
                val id=UUID.randomUUID().toString()
                if(!prefs.edit().putString(owner,id).commit()){info="Não consegui proteger este pedido. Nenhum envio iniciado."}
                else{pending=id;busy=true;scope.launch{try{
                    val result=assistantRequest(app,owner,"google-mail-send",JSONObject().put("request_id",id).put("connection_id",selected!!.getString("id"))
                        .put("recipient",recipient.trim()).put("subject",subject).put("body",text).put("reviewed",true),allowCached=false)
                    if(app.auth.account.value?.id==owner){info=result.optString("reply");if(result.optBoolean("sent")){clearPending();recipient="";subject="";text=""}}
                }catch(e:Exception){if(e is CancellationException)throw e;info="Não confirmei o envio. Use Conferir último envio antes de preparar outro."}
                finally{busy=false}}}
            }
        }){Text("Enviar pelo Gmail")}},dismissButton={TextButton(onClick={review=false}){Text("Editar")}})
}
