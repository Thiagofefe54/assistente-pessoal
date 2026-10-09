package com.thiago.assistentepessoal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thiago.assistentepessoal.routine.diaryCategories
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.ZoneId
import java.util.UUID

@Composable
fun CompanionScreen(onBack:()->Unit,onArea:(String)->Unit){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val owner=account?.id
    if(owner==null){Column(Modifier.padding(22.dp)){TextButton(onClick=onBack){Text("← Voltar")};Text("Entre na sua conta para acompanhar seu ritmo.")};return}
    key(owner){ConnectedCompanion(app,owner,onBack,onArea)}
}

@Composable private fun ConnectedCompanion(app:KoiwaiApplication,owner:String,onBack:()->Unit,onArea:(String)->Unit){
    val prefs=remember(owner){app.getSharedPreferences("koi-companion-$owner",0)}
    var review by remember{mutableStateOf<JSONObject?>(null)}
    var busy by remember{mutableStateOf(false)}
    var info by remember{mutableStateOf<String?>(null)}
    var tab by rememberSaveable{mutableStateOf("day")}
    var category by rememberSaveable{mutableStateOf("study")}
    var note by rememberSaveable{mutableStateOf("")}
    var testData by remember{mutableStateOf(prefs.getBoolean("test-data",true))}
    var pending by remember{mutableStateOf(prefs.getString("pending",null))}
    var lastReceipt by remember{mutableStateOf(prefs.getString("receipt",null))}
    val scope=rememberCoroutineScope()
    suspend fun refresh(){val value=assistantRequest(app,owner,"review");if(app.auth.account.value?.id==owner)review=value}
    fun load(){if(busy)return;busy=true;info=null;scope.launch{
        try{refresh()}catch(e:CancellationException){throw e}catch(e:Exception){info=e.message}finally{busy=false}
    }}
    fun perform(path:String,body:JSONObject,retry:Boolean=false){
        if(busy || (pending!=null && !retry))return
        if(!body.has("request_id"))body.put("request_id",UUID.randomUUID().toString())
        if(!body.has("timezone"))body.put("timezone",ZoneId.systemDefault().id)
        val saved=JSONObject().put("path",path).put("body",body).toString()
        if(!prefs.edit().putString("pending",saved).commit()){info="Não consegui guardar este pedido no celular. Tente novamente.";return}
        pending=saved;busy=true;info=null
        scope.launch{
            try{
                val result=assistantRequest(app,owner,path,body)
                if(app.auth.account.value?.id==owner){
                    pending=null;prefs.edit().remove("pending").commit()
                    val receipt=result.getJSONObject("receipt")
                    lastReceipt=if(receipt.optBoolean("undone"))null else receipt.toString()
                    prefs.edit().putString("receipt",lastReceipt).commit()
                    info=result.getString("message");note=""
                    app.tasks.value?.refresh();app.personal.value?.refresh()
                    try{refresh()}catch(e:CancellationException){throw e}catch(_:Exception){info="Ação confirmada. Toque em Atualizar para conferir a lista."}
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){
                if(e.message?.contains("registro mudou")==true || e.message?.startsWith("Confira os dados:")==true){pending=null;prefs.edit().remove("pending").commit()}
                info=e.message ?: "Não consegui confirmar. Repita o mesmo pedido abaixo."
            }finally{busy=false}
        }
    }
    LaunchedEffect(owner){load()}
    val canAct=!busy && pending==null
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            TextButton(onClick=onBack){Text("← Voltar")}
            Eyebrow("UM PASSO DE CADA VEZ",KoiColors.Purple)
            Text("Seu ritmo.\nCom a Koi.",fontSize=30.sp,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("day" to "Meu dia","habits" to "Hábitos","checkins" to "Check-ins").forEach{(id,label)->FilterChip(selected=tab==id,onClick={tab=id},label={Text(label)})}
            }
            TextButton(onClick={load()},enabled=!busy){Text("Atualizar acompanhamento")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            info?.let{Text(it,color=KoiColors.Blue,fontSize=13.sp)}
            pending?.let{value->
                Text("Há uma ação aguardando confirmação. Repetir usa o mesmo pedido para evitar duplicação.",color=KoiColors.Muted,fontSize=12.sp)
                KoiAction("Conferir / tentar novamente",{val p=JSONObject(value);perform(p.getString("path"),p.getJSONObject("body"),true)},enabled=!busy)
            }
            lastReceipt?.let{value->
                TextButton(enabled=canAct,onClick={val r=JSONObject(value);perform("undo",JSONObject().put("request_id",r.getString("request_id")).put("target_kind",r.getString("target_kind")))}){Text("↶ Desfazer última ação")}
            }
        }
        if(tab=="checkins"){
            item{KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
                Text("Como está seu momento?",fontSize=20.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    diaryCategories.forEach{(id,label)->FilterChip(selected=category==id,onClick={category=id},label={Text(label)})}
                }
                OutlinedTextField(note,{note=it.take(2000)},label={Text("Quer contar algo? (opcional)")},modifier=Modifier.fillMaxWidth(),maxLines=4)
                Row{Text("Marcar como Teste",Modifier.weight(1f));Switch(checked=testData,onCheckedChange={testData=it;prefs.edit().putBoolean("test-data",it).apply()})}
                Text("O horário é registrado ao salvar. Iniciar e finalizar cria um intervalo no Diário; sono é informado por você.",color=KoiColors.Muted,fontSize=12.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    KoiAction(if(category=="sleep")"Vou dormir" else "Comecei",{perform("checkin",JSONObject().put("category",category).put("phase","start").put("note",note).put("test_data",testData))},enabled=canAct)
                    TextButton(enabled=canAct,onClick={perform("checkin",JSONObject().put("category",category).put("phase","note").put("note",note).put("test_data",testData))}){Text("Registrar momento")}
                }
            }}
            review?.getJSONArray("active_checkins")?.let{rows->
                item{Text("Atividades iniciadas · últimas 48h",fontWeight=FontWeight.SemiBold)}
                if(rows.length()==0)item{Text("Nenhum início aberto. Você pode registrar um acima.",color=KoiColors.Muted)}
                for(i in 0 until rows.length()){val r=rows.getJSONObject(i)
                    item(key="active-${r.getString("id")}"){KoiPanel(Modifier.fillMaxWidth()){
                        Text(r.getString("title"));Text(r.getString("started_at"),fontSize=12.sp,color=KoiColors.Muted)
                        KoiAction(if(r.getString("category")=="sleep")"Acordei" else "Terminei",{perform("checkin",JSONObject().put("category",r.getString("category")).put("phase","finish").put("record_id",r.getString("id")).put("expected_updated_at",r.getString("updated_at")))},enabled=canAct)
                    }}
                }
            }
            item{TextButton(onClick={onArea("Diário")}){Text("Abrir Diário / corrigir registros →")}}
        }
        review?.let{value->
            if(tab=="day"){
                item{KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
                    Text("Revisão · ${value.getString("date")}",fontSize=20.sp)
                    Text("${value.getInt("completed_count")} conquistas · ${value.getInt("pending_count")} pendências",fontWeight=FontWeight.SemiBold)
                    Text("${value.getInt("diary_count")} momentos no Diário",color=KoiColors.Muted)
                    if(!value.isNull("sleep_minutes")){val n=value.getInt("sleep_minutes");Text("Sono informado: ${n/60}h ${n%60}min · ${value.getInt("sleep_intervals")} intervalo(s) encerrados hoje")}
                    val done=value.getJSONArray("completed")
                    for(i in 0 until done.length()){val r=done.getJSONObject(i);Text("✓ ${r.getString("time")} · ${r.getString("title")}",fontSize=13.sp)}
                    Text(value.getString("encouragement"),color=KoiColors.Purple)
                }}
                val alerts=value.getJSONArray("alerts")
                for(i in 0 until alerts.length()){val r=alerts.getJSONObject(i)
                    item{KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Red){Text(r.getString("text"));TextButton(onClick={onArea(r.getString("area"))}){Text("Conferir ${r.getString("area")} →")}}}
                }
                val suggestions=value.getJSONArray("suggestions")
                if(suggestions.length()>0)item{Text("Podemos reorganizar?",fontSize=20.sp,fontWeight=FontWeight.SemiBold)}
                for(i in 0 until suggestions.length()){val r=suggestions.getJSONObject(i)
                    item{KoiPanel(Modifier.fillMaxWidth()){
                        Text(r.getString("title"),fontWeight=FontWeight.SemiBold)
                        Text(r.getString("reason"),fontSize=13.sp,color=KoiColors.Muted)
                        Text("${r.getString("target_date")} · ${r.getString("time").ifBlank{"sem horário"}} · ${r.getString("timezone")}",fontSize=12.sp)
                        KoiAction("Levar para essa data",{perform("task-action",JSONObject().put("task_id",r.getString("task_id")).put("expected_updated_at",r.getString("updated_at")).put("action","reschedule").put("target_date",r.getString("target_date")))},enabled=canAct)
                    }}
                }
            }
            if(tab=="habits"){
                item{Text("Sua constância, sem cobrança 💜",fontSize=20.sp);Text("Dias com conclusão nos últimos 7 dias. Isso não define uma meta diária para hábitos semanais ou mensais.",fontSize=12.sp,color=KoiColors.Muted)}
                val rows=value.getJSONArray("habits")
                if(rows.length()==0)item{Text("Crie um hábito recorrente para acompanhar aqui.")}
                for(i in 0 until rows.length()){val r=rows.getJSONObject(i)
                    item{KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
                        Text(r.getString("title"),fontWeight=FontWeight.SemiBold)
                        Text("${r.getInt("days_done")} de 7 dias com registro${if(r.getBoolean("done_today"))" · fez hoje ✨" else ""}")
                        LinearProgressIndicator(progress={r.getInt("days_done")/7f},modifier=Modifier.fillMaxWidth())
                        Text("Próxima etapa: ${r.optString("date")} ${r.getString("time")}",color=KoiColors.Muted,fontSize=12.sp)
                        KoiAction("Concluir esta etapa",{perform("task-action",JSONObject().put("task_id",r.getString("id")).put("expected_updated_at",r.getString("updated_at")).put("action","complete"))},enabled=canAct && r.getBoolean("can_complete"))
                    }}
                }
                item{TextButton(onClick={onArea("Hábitos")}){Text("Criar ou editar hábitos →")}}
            }
            item{
                if(value.getBoolean("partial"))Text("Consulta limitada. Confira os demais registros nas respectivas telas.",color=KoiColors.Red,fontSize=12.sp)
                Text(value.getString("scope"),color=KoiColors.Muted,fontSize=11.sp)
                Text("Atualize após alterações. Este painel não gera resposta de IA.",color=KoiColors.Muted,fontSize=11.sp)
            }
        }
    }
}
