package com.thiago.assistentepessoal.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable fun GoogleConnectionPanel(){
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val owner=account?.id
    val scope=rememberCoroutineScope()
    var status by remember(owner){mutableStateOf<JSONObject?>(null)}
    var result by remember(owner){mutableStateOf<JSONObject?>(null)}
    var busy by remember(owner){mutableStateOf(false)}
    var info by remember(owner){mutableStateOf<String?>(null)}
    var removing by remember(owner){mutableStateOf<JSONObject?>(null)}
    fun request(path:String,body:JSONObject=JSONObject()){
        if(owner==null || busy)return
        busy=true;info=null;result=null
        scope.launch{
            try{
                val value=assistantRequest(app,owner,path,body,allowCached=false)
                if(app.auth.account.value?.id!=owner)return@launch
                when(path){
                    "google-status"->status=value
                    "google-read"->result=value
                    "google-connect"->{
                        val uri=Uri.parse(value.getString("url"))
                        val backend=Uri.parse(BuildConfig.BACKEND_URL)
                        require(uri.scheme=="https" && uri.host==backend.host && uri.port==backend.port &&
                            uri.path=="/api/v1/connections/google/begin" && uri.userInfo==null && uri.fragment==null)
                        openIntent(context,Intent(Intent.ACTION_VIEW,uri))
                        info="Escolha uma conta e revise as permissões no Google. Ao voltar, atualize a lista."
                    }
                    "google-disconnect"->{
                        info="Conexão removida da Koi. Para revogar a autorização Google, use as configurações da sua conta Google."
                        status=assistantRequest(app,owner,"google-status",allowCached=false)
                    }
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(app.auth.account.value?.id==owner)info=e.message?:"Não consegui consultar as conexões Google."}
            finally{if(app.auth.account.value?.id==owner)busy=false}
        }
    }
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle,owner){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)request("google-status")}
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(owner){if(owner!=null)request("google-status")}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
        Eyebrow("GOOGLE COM A KOI",KoiColors.Purple)
        Text("Suas contas, separadinhas",fontSize=22.sp)
        Text("Até três contas · consultas sem pontos de IA",fontSize=12.sp,color=KoiColors.Muted)
        if(owner==null)Text("Entre na sua conta Koiwai para conectar o Google.")
        KoiAction(if(busy)"Conferindo…" else "Atualizar contas",{request("google-status")},enabled=owner!=null && !busy)
        val configured=status?.optBoolean("configured")==true
        val rows=status?.optJSONArray("accounts")
        if(status!=null && !configured)Text("O servidor ainda está preparando a conexão Google. Nenhuma conta foi autorizada pelo app.",fontSize=12.sp,color=KoiColors.Muted)
        KoiAction("Adicionar / reautorizar conta Google",{request("google-connect")},enabled=owner!=null && configured && !busy)
        Text("Você revisa os acessos no Google. Senhas e tokens não entram no app. Conectar a mesma conta atualiza seu vínculo.",fontSize=12.sp,color=KoiColors.Muted)
        if(rows!=null && rows.length()==0 && configured)Text("Nenhuma conta conectada ainda.")
        if(rows!=null)for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i)
            Text(row.getString("email"),fontSize=16.sp)
            val services=row.getJSONArray("services")
            listOf("calendar" to "Próximos eventos · agenda principal","calendars" to "Minhas agendas",
                "tasks" to "Listas do Google Tasks","task_items" to "Tarefas nas listas","mail" to "Mensagens recentes","drive" to "Arquivos recentes").forEach{(service,label)->
                if((0 until services.length()).any{services.getString(it)==if(service=="task_items")"tasks" else service}){
                    TextButton(enabled=!busy,onClick={request("google-read",JSONObject().put("connection_id",row.getString("id")).put("service",service))}){Text(label)}
                }
            }
            TextButton(enabled=!busy,onClick={removing=row}){Text("Desconectar esta conta",color=KoiColors.Red)}
            HorizontalDivider()
        }
        info?.let{Text(it,fontSize=12.sp,color=KoiColors.Muted)}
        result?.let{value->
            Text("Consulta · ${value.getJSONObject("account").getString("email")}",fontSize=14.sp)
            val items=value.getJSONArray("items")
            if(items.length()==0)Text("Nenhum item nesta consulta.",fontSize=12.sp)
            for(i in 0 until items.length()){
                val item=items.getJSONObject(i)
                Text(item.optString("summary",item.optString("title",item.optString("name",item.optString("subject","Mensagem ${i+1}")))),fontSize=14.sp)
                item.optJSONObject("start")?.let{start->Text(start.optString("dateTime",start.optString("date")),fontSize=12.sp,color=KoiColors.Muted)}
                item.optString("from").takeIf{it.isNotBlank()}?.let{Text(it,fontSize=12.sp,color=KoiColors.Muted)}
            }
            if(value.optBoolean("partial"))Text("Exibindo uma parte dos resultados.",fontSize=12.sp,color=KoiColors.Muted)
            Text("Dados consultados agora; não são enviados à IA nem guardados no cache do celular.",fontSize=11.sp,color=KoiColors.Muted)
        }
        Text("O chat consulta Google e pode criar/ajustar Agenda e Tasks quando você pedir e identificar a conta. Rascunhos, conteúdo de arquivos e envio de e-mail ainda não estão disponíveis.",fontSize=11.sp,color=KoiColors.Muted)
    }
    removing?.let{row->AlertDialog(onDismissRequest={removing=null},title={Text("Desconectar Google?")},
        text={Text("Remover ${row.getString("email")} da Koi. Seus dados no Google serão preservados.")},
        confirmButton={TextButton(onClick={removing=null;request("google-disconnect",JSONObject().put("connection_id",row.getString("id")))}){Text("Desconectar")}},
        dismissButton={TextButton(onClick={removing=null}){Text("Cancelar")}})}
}
