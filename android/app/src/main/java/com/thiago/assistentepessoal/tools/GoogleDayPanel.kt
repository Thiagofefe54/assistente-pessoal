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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun googleClock(raw:String):String=runCatching{
    OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(raw.take(25))

@Composable internal fun GooglePlanDetails(plan:JSONObject){
    Text("Rotina da Koi · ${plan.getString("date")}",fontSize=15.sp,color=KoiColors.Purple)
    val conflicts=plan.getJSONArray("conflicts")
    for(i in 0 until conflicts.length()){val r=conflicts.getJSONObject(i);Text("Horário ocupado: ${r.getString("time")} · ${r.getString("title")}",fontSize=12.sp,color=KoiColors.Red)}
    val windows=plan.getJSONArray("windows")
    for(i in 0 until windows.length()){val r=windows.getJSONObject(i);Text("Janela sem eventos consultados: ${r.getString("start")}–${r.getString("end")}",fontSize=12.sp)}
    val scheduled=plan.getJSONArray("scheduled")
    for(i in 0 until scheduled.length()){val r=scheduled.getJSONObject(i);Text("Missão com horário: ${r.getString("time")} · ${r.getString("title")}",fontSize=12.sp)}
    val priorities=plan.getJSONArray("priorities")
    for(i in 0 until priorities.length())Text("Prioridade: ${priorities.getJSONObject(i).getString("title")}",fontSize=12.sp)
    Text(plan.getString("note"),fontSize=11.sp,color=KoiColors.Muted)
    if(plan.optBoolean("partial"))Text("Este cruzamento é parcial. Confira as contas que faltaram.",fontSize=12.sp,color=KoiColors.Red)
}

@Composable internal fun GoogleDayResult(value:JSONObject){
    Text("Seu dia conectado · ${value.getString("date")}",fontSize=18.sp,color=KoiColors.Blue)
    val accounts=value.getJSONArray("accounts")
    Text("${accounts.length()} contas consultadas · ${value.getJSONArray("items").length()} eventos",fontSize=12.sp)
    val errors=value.getJSONArray("errors")
    for(i in 0 until errors.length()){val r=errors.getJSONObject(i);Text("${r.getString("email")}: ${r.getString("message")}",fontSize=12.sp,color=KoiColors.Red)}
    val items=value.getJSONArray("items")
    for(i in 0 until items.length()){
        val r=items.getJSONObject(i)
        Text("${if(r.optBoolean("all_day"))"Dia inteiro" else googleClock(r.getString("starts_at"))+"–"+googleClock(r.getString("ends_at"))} · ${r.getString("title")}",fontSize=14.sp)
        Text(r.getString("email")+if(r.optBoolean("blocks_time",true))"" else " · não bloqueia horário",fontSize=11.sp,color=KoiColors.Muted)
    }
    val collisions=value.getJSONArray("calendar_conflicts")
    if(value.getInt("calendar_conflict_count")>0)Text("Sobreposições: ${value.getInt("calendar_conflict_count")}",fontSize=15.sp,color=KoiColors.Red)
    for(i in 0 until collisions.length()){
        val r=collisions.getJSONObject(i);val a=r.getJSONObject("first");val b=r.getJSONObject("second")
        Text("${googleClock(r.getString("start"))}–${googleClock(r.getString("end"))}: ${a.getString("title")} ↔ ${b.getString("title")}",fontSize=12.sp,color=KoiColors.Red)
        Text("${a.getString("email")} ↔ ${b.getString("email")}",fontSize=11.sp,color=KoiColors.Muted)
    }
    if(value.optBoolean("conflicts_partial"))Text("Mostrando as primeiras vinte sobreposições.",fontSize=11.sp)
    GooglePlanDetails(value.getJSONObject("plan"))
}

@Composable fun GoogleDayPanel(){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState();val owner=account?.id
    val scope=rememberCoroutineScope()
    var day by remember(owner){mutableStateOf(LocalDate.now())}
    var value by remember(owner){mutableStateOf<JSONObject?>(null)}
    var error by remember(owner){mutableStateOf<String?>(null)}
    var busy by remember(owner){mutableStateOf(false)}
    fun load(){if(owner==null || busy)return;busy=true;error=null;value=null
        scope.launch{try{val result=assistantRequest(app,owner,"google-day",JSONObject().put("day",day.toString()),allowCached=false)
            if(app.auth.account.value?.id==owner)value=result
        }catch(e:CancellationException){throw e}
        catch(e:Exception){if(app.auth.account.value?.id==owner)error=e.message?:"Não consegui cruzar suas agendas."}
        finally{if(app.auth.account.value?.id==owner)busy=false}}
    }
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Eyebrow("MEU DIA CONECTADO",KoiColors.Blue)
        Text("Três contas, uma visão do dia",fontSize=20.sp)
        Text("Agendas principais + tarefas da Koi · sem pontos de IA",fontSize=12.sp,color=KoiColors.Muted)
        Row{
            TextButton(enabled=!busy,onClick={day=day.minusDays(1);value=null;error=null}){Text("← Dia")}
            TextButton(enabled=!busy,onClick={day=LocalDate.now();value=null;error=null}){Text("Hoje")}
            TextButton(enabled=!busy,onClick={day=day.plusDays(1);value=null;error=null}){Text("Dia →")}
        }
        Text(day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),fontSize=16.sp)
        KoiAction(if(busy)"Cruzando suas agendas…" else "Consultar e cruzar meu dia",{load()},enabled=owner!=null && !busy)
        if(owner==null)Text("Entre na Koi para consultar as contas conectadas.",fontSize=12.sp)
        error?.let{Text(it,fontSize=12.sp,color=KoiColors.Red)}
        value?.let{GoogleDayResult(it)}
        Text("Consulta manual. Não muda compromissos nem guarda dados Google no histórico ou cache.",fontSize=11.sp,color=KoiColors.Muted)
    }
}

@Composable fun GooglePrivacySettings(){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("koiwai-preferences",0)}
    var automatic by remember{mutableStateOf(prefs.getBoolean("google-auto-read",true))}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Eyebrow("CONSULTAS GOOGLE",KoiColors.Blue)
        Text("Abrir resultados de novos pedidos",fontSize=16.sp)
        Switch(checked=automatic,onCheckedChange={automatic=it;prefs.edit().putBoolean("google-auto-read",it).apply()})
        Text(if(automatic)"O cartão consulta quando um novo pedido aparece no chat aberto." else "Toque em Atualizar consulta para buscar os dados. Pedidos de alteração continuam funcionando.",fontSize=12.sp,color=KoiColors.Muted)
        Text("Cartões antigos sempre exigem toque. Conteúdo consultado fica apenas na tela; senhas e tokens ficam no servidor. Contas e permissões são gerenciadas em Conexões.",fontSize=11.sp,color=KoiColors.Muted)
    }
}
