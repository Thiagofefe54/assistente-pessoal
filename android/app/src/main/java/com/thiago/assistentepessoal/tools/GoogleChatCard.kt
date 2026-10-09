package com.thiago.assistentepessoal.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** Live, owner-scoped Google results: not persisted into chat/history/cache. */
@Composable fun GoogleChatCard(raw:String,autoRead:Boolean){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val owner=account?.id
    val receipt=remember(raw){JSONObject(raw)}
    var results by remember(owner,raw){mutableStateOf<List<Pair<String,JSONObject?>>>(emptyList())}
    var busy by remember(owner,raw){mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun load(){
        if(owner==null || busy)return
        busy=true;results=emptyList()
        scope.launch{
            try{
                val ids=receipt.getJSONArray("connection_ids")
                require(ids.length() in 1..3)
                val values=coroutineScope{
                    (0 until ids.length()).map{index->async{
                        try{
                            val body=JSONObject().put("connection_id",ids.getString(index)).put("service",receipt.getString("service"))
                            listOf("start","end","query").forEach{key->if(!receipt.isNull(key))body.put(key,receipt.getString(key))}
                            body.put("plan",receipt.optBoolean("plan"))
                            "" to assistantRequest(app,owner,"google-read",body,allowCached=false)
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){(e.message?:"Não consegui consultar esta conta.") to null}
                    }}.awaitAll()
                }
                if(app.auth.account.value?.id==owner)results=values
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(app.auth.account.value?.id==owner)results=listOf((e.message?:"Não consegui abrir a consulta Google.") to null)}
            finally{if(app.auth.account.value?.id==owner)busy=false}
        }
    }
    LaunchedEffect(owner,raw,autoRead){if(autoRead)load()}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Eyebrow("GOOGLE · CONSULTA PRIVADA",KoiColors.Blue)
        Text(when(receipt.optString("service")){"calendar"->"Sua agenda Google";"task_items"->"Suas tarefas Google";"mail"->"Mensagens recentes";else->"Arquivos recentes"},fontSize=18.sp)
        KoiAction(if(busy)"Consultando suas contas…" else "Atualizar consulta",{load()},enabled=owner!=null && !busy)
        results.forEach{(error,data)->
            if(data==null)Text(error,fontSize=12.sp,color=KoiColors.Red)
            else{
                Text(data.getJSONObject("account").getString("email"),fontSize=14.sp,color=KoiColors.Blue)
                val items=data.getJSONArray("items")
                if(items.length()==0)Text("Nenhum item encontrado nesta consulta.",fontSize=12.sp)
                for(i in 0 until items.length()){
                    val item=items.getJSONObject(i)
                    Text((if(item.optString("status")=="completed")"✓ " else "• ")+item.optString("title",item.optString("summary",item.optString("name",item.optString("subject","Sem título")))),fontSize=14.sp)
                    item.optJSONObject("start")?.let{Text(it.optString("dateTime",it.optString("date")),fontSize=12.sp,color=KoiColors.Muted)}
                    listOf("due","list","from").forEach{key->item.optString(key).takeIf{it.isNotBlank()}?.let{Text(it,fontSize=12.sp,color=KoiColors.Muted)}}
                }
                if(data.optBoolean("partial"))Text("Consulta parcial: existem itens fora deste recorte.",fontSize=12.sp,color=KoiColors.Muted)
                data.optString("note").takeIf{it.isNotBlank()}?.let{Text(it,fontSize=11.sp,color=KoiColors.Muted)}
                data.optJSONObject("plan")?.let{plan->
                    Text("Seu dia com a Rotina da Koi · ${plan.getString("date")}",fontSize=15.sp,color=KoiColors.Purple)
                    val conflicts=plan.getJSONArray("conflicts")
                    for(i in 0 until conflicts.length()){val r=conflicts.getJSONObject(i);Text("Horário ocupado: ${r.getString("time")} · ${r.getString("title")}",fontSize=12.sp,color=KoiColors.Red)}
                    val windows=plan.getJSONArray("windows")
                    for(i in 0 until windows.length()){val r=windows.getJSONObject(i);Text("Sem evento nesta agenda: ${r.getString("start")}–${r.getString("end")}",fontSize=12.sp)}
                    val priorities=plan.getJSONArray("priorities")
                    for(i in 0 until priorities.length())Text("Prioridade da Koi: ${priorities.getJSONObject(i).getString("title")}",fontSize=12.sp)
                    Text(plan.getString("note"),fontSize=11.sp,color=KoiColors.Muted)
                    if(plan.optBoolean("partial"))Text("Este cruzamento é parcial.",fontSize=12.sp,color=KoiColors.Muted)
                }
                HorizontalDivider()
            }
        }
        Text("Resultados ao vivo, separados por conta. Não são guardados no histórico nem enviados à IA.",fontSize=11.sp,color=KoiColors.Muted)
    }
}
