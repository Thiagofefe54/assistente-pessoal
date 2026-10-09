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
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val prefs=remember{context.getSharedPreferences("koiwai-preferences",0)}
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
                val values=if(receipt.optBoolean("plan") && receipt.optString("service")=="calendar"){
                    val body=JSONObject().put("connection_ids",ids)
                    if(!receipt.isNull("start"))body.put("day",receipt.getString("start"))
                    listOf("" to assistantRequest(app,owner,"google-day",body,allowCached=false))
                }else coroutineScope{
                    (0 until ids.length()).map{index->async{
                        try{
                            val body=JSONObject().put("connection_id",ids.getString(index)).put("service",receipt.getString("service"))
                            listOf("start","end","query").forEach{key->if(!receipt.isNull(key))body.put(key,receipt.getString(key))}
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
    LaunchedEffect(owner,raw,autoRead){if(autoRead && prefs.getBoolean("google-auto-read",true))load()}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Eyebrow("GOOGLE · CONSULTA PRIVADA",KoiColors.Blue)
        Text(when(receipt.optString("service")){"calendar"->"Sua agenda Google";"task_items"->"Suas tarefas Google";"mail"->"Mensagens recentes";else->"Arquivos recentes"},fontSize=18.sp)
        KoiAction(if(busy)"Consultando suas contas…" else "Atualizar consulta",{load()},enabled=owner!=null && !busy)
        results.forEach{(error,data)->
            if(data==null)Text(error,fontSize=12.sp,color=KoiColors.Red)
            else{
                if(data.has("accounts"))GoogleDayResult(data) else GoogleReadResults(data)
                HorizontalDivider()
            }
        }
        Text("Resultados ao vivo, separados por conta. Não são guardados no histórico nem enviados à IA.",fontSize=11.sp,color=KoiColors.Muted)
    }
}
