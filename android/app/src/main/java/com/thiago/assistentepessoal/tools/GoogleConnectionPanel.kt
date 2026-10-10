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
    GoogleDayPanel()
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
    var search by remember(owner){mutableStateOf(false)}
    var query by remember(owner){mutableStateOf("")}
    var first by remember(owner){mutableStateOf("")}
    var last by remember(owner){mutableStateOf("")}
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
    fun readAccount(id:String,service:String,selection:String?=null){
        val body=JSONObject().put("connection_id",id).put("service",service)
        selection?.let{body.put(if(service=="calendar")"calendar_id" else "list_id",it)}
        if(search && service in listOf("calendar","task_items","mail","drive")){
            try{
                val a=first.trim().takeIf{it.isNotEmpty()}?.let{java.time.LocalDate.parse(it)}
                val b=last.trim().takeIf{it.isNotEmpty()}?.let{java.time.LocalDate.parse(it)}
                require(b==null || a!=null){"Preencha a data inicial também."}
                require(a==null || b==null || java.time.temporal.ChronoUnit.DAYS.between(a,b) in 0..31){"Use um período de até 31 dias."}
                a?.let{body.put("start",it.toString())};b?.let{body.put("end",it.toString())}
                if(query.trim().isNotEmpty())body.put("query",query.trim())
            }catch(e:Exception){info=e.message?:"Use datas no formato AAAA-MM-DD.";return}
        }
        request("google-read",body)
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
        TextButton(enabled=!busy,onClick={search=!search}){Text(if(search)"Fechar busca e datas" else "Buscar por termo ou data")}
        if(search){
            OutlinedTextField(query,{query=it.take(200)},label={Text("Título, assunto ou nome do arquivo")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(first,{first=it.take(10)},label={Text("De · AAAA-MM-DD (opcional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(last,{last=it.take(10)},label={Text("Até · AAAA-MM-DD (opcional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("Sem data final, consulta só o dia inicial. Gmail filtra recebimento; Drive filtra última modificação. Escolha a conta e o serviço abaixo.",fontSize=11.sp,color=KoiColors.Muted)
            TextButton(onClick={query="";first="";last=""}){Text("Limpar filtros")}
        }
        if(rows!=null)for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i)
            Text(row.getString("email"),fontSize=16.sp)
            val services=row.getJSONArray("services")
            listOf("calendar" to "Próximos eventos · agenda principal","calendars" to "Minhas agendas",
                "tasks" to "Listas do Google Tasks","task_items" to "Tarefas nas listas","mail" to "Mensagens recentes","drive" to "Arquivos recentes").forEach{(service,label)->
                if((0 until services.length()).any{services.getString(it)==if(service=="task_items")"tasks" else service}){
                    TextButton(enabled=!busy,onClick={readAccount(row.getString("id"),service)}){Text(label)}
                }
            }
            TextButton(enabled=!busy,onClick={removing=row}){Text("Desconectar esta conta",color=KoiColors.Red)}
            HorizontalDivider()
        }
        info?.let{Text(it,fontSize=12.sp,color=KoiColors.Muted)}
        result?.let{value->
            GoogleReadResults(value){service,selection->readAccount(value.getJSONObject("account").getString("id"),service,selection)}
            Text("Dados consultados agora; não são enviados à IA nem guardados no cache do celular.",fontSize=11.sp,color=KoiColors.Muted)
        }
        Text("O chat consulta Google e pode criar/ajustar Agenda e Tasks. Para enviar e-mail, reautorize a conta e use Escrever e-mail abaixo.",fontSize=11.sp,color=KoiColors.Muted)
    }
    GoogleMailPanel(status)
    GoogleMailAlertsPanel()
    removing?.let{row->AlertDialog(onDismissRequest={removing=null},title={Text("Desconectar Google?")},
        text={Text("Remover ${row.getString("email")} da Koi. Seus dados no Google serão preservados.")},
        confirmButton={TextButton(onClick={removing=null;request("google-disconnect",JSONObject().put("connection_id",row.getString("id")))}){Text("Desconectar")}},
        dismissButton={TextButton(onClick={removing=null}){Text("Cancelar")}})}
}
