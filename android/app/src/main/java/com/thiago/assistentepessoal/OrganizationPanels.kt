package com.thiago.assistentepessoal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.LocalDate

/** Manual account-scoped panel; no automatic polling or AI generation. */
@Composable
private fun OrganizationPanel(title:String,subtitle:String,path:String,request:()->JSONObject= {JSONObject()},content:@Composable (JSONObject)->Unit){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState();val owner=account?.id
    var data by remember(owner){mutableStateOf<JSONObject?>(null)}
    var busy by remember(owner){mutableStateOf(false)}
    var error by remember(owner){mutableStateOf<String?>(null)}
    var expanded by remember(owner){mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
        Text(title,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
        Text(subtitle,color=KoiColors.Muted,fontSize=12.sp)
        KoiAction(if(busy)"Conferindo…" else if(path=="demo")"Criar exemplos de teste" else "${if(data==null)"Consultar" else "Atualizar"} • $title",{
            if(owner!=null){busy=true;error=null
                scope.launch{
                    try{val result=assistantRequest(app,owner,path,request());if(app.auth.account.value?.id==owner){data=result;expanded=true
                        if(path=="demo"){
                            app.getSharedPreferences("koi-demo",0).edit().putString("manifest-$owner",result.toString()).apply()
                            app.personal.value?.refresh();app.tasks.value?.refresh();app.memories.value?.refresh()
                        }
                    }}catch(cancelled:CancellationException){throw cancelled}
                    catch(failure:Exception){if(app.auth.account.value?.id==owner)error=failure.message ?: "Não consegui consultar."}
                    finally{if(app.auth.account.value?.id==owner)busy=false}
                }
            }
        },Modifier.fillMaxWidth(),owner!=null && !busy)
        if(owner==null)Text("Entre na sua conta para usar este painel.",color=KoiColors.Muted,fontSize=12.sp)
        error?.let{Text(it,color=KoiColors.Red,fontSize=12.sp)}
        data?.let{value->
            TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Recolher ↑" else "Ver resultado ↓")}
            AnimatedVisibility(expanded){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){content(value)}}
        }
    }
}

@Composable fun DayPlanPanel(onRoutine:()->Unit){
    OrganizationPanel("Plano do dia","Seus horários e uma ordem sugerida para as pendências. Sem mudar tarefas.","plan"){value->
        Text("${value.getString("date")} · ${value.getInt("eligible_count")} pendências",fontWeight=FontWeight.SemiBold)
        for((key,label) in listOf("scheduled" to "Já tem horário","priorities" to "Prioridades sugeridas")){
            Text(label,color=KoiColors.Blue)
            val rows=value.getJSONArray(key)
            if(rows.length()==0)Text("Nenhuma neste grupo 💜",fontSize=12.sp)
            for(index in 0 until rows.length()){
                val r=rows.getJSONObject(index)
                Text("${r.getString("time").let{if(it.isBlank())"✦" else it}} · ${r.getString("title")}${if(r.getBoolean("overdue"))" · atrasada" else ""}",fontSize=13.sp)
            }
        }
        if(value.getBoolean("partial"))Text("Plano resumido; confira todas na Rotina.",color=KoiColors.Red,fontSize=12.sp)
        Text(value.getString("note"),fontSize=11.sp,color=KoiColors.Muted)
        TextButton(onClick=onRoutine){Text("Abrir Rotina →")}
    }
}

@Composable fun RecallPanel(onDay:(String)->Unit,onFacts:()->Unit,onArea:(String)->Unit){
    var query by remember{mutableStateOf("")};var days by remember{mutableStateOf(90)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        OutlinedTextField(query,{query=it.take(120)},label={Text("O que você quer recordar?")},placeholder={Text("Academia, estudo, lanche…")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf(7,30,90).forEach{n->FilterChip(selected=days==n,onClick={days=n},label={Text("${n} dias")})}
        }
        OrganizationPanel("Buscar lembranças","Por palavras, no diário, nos registros e no que você contou. Sem gastar pontos de IA.","search",{
            val today=LocalDate.now();JSONObject().put("query",query).put("start",today.minusDays((days-1).toLong()).toString()).put("end",today.toString())
        }){value->
            Text("Busca: ${value.getString("query").ifBlank{"acontecimentos do período"}}",fontWeight=FontWeight.SemiBold)
            Text("${value.getString("start")} → ${value.getString("end")}",fontSize=12.sp,color=KoiColors.Muted)
            val rows=value.getJSONArray("results")
            if(rows.length()==0)Text("Não encontrei esse assunto nos dados consultados. Tente menos palavras 💜")
            for(index in 0 until rows.length()){
                val r=rows.getJSONObject(index)
                HorizontalDivider(color=KoiColors.Purple.copy(alpha=.2f))
                Text("${r.getString("area")} · ${if(r.isNull("day"))"Sem data do acontecimento" else r.getString("day")}",color=KoiColors.Blue,fontSize=12.sp)
                Text(r.getString("excerpt"),fontSize=13.sp)
                if(r.getString("source")=="chat" && !r.isNull("day"))TextButton(onClick={onDay(r.getString("day"))}){Text("Abrir conversa de origem →")}
                if(r.getString("source")=="memory")TextButton(onClick=onFacts){Text("Abrir lembranças →")}
                if(r.getString("source") in listOf("record","diary"))TextButton(onClick={onArea(r.getString("area"))}){Text("Abrir ${r.getString("area")} →")}
            }
            if(value.getBoolean("partial"))Text("Resultado limitado; refine assunto ou período.",color=KoiColors.Red,fontSize=12.sp)
            Text(value.getString("scope"),fontSize=11.sp,color=KoiColors.Muted)
        }
    }
}

@Composable fun DemoDataPanel(){
    OrganizationPanel("Laboratório da Koi","12 exemplos marcados Teste: tarefas, diário, lembrança, contas e categorias. Valores fictícios entram nos totais. Repetir não duplica.","demo"){value->
        Text("${value.getInt("count")} exemplos preparados 💜",fontWeight=FontWeight.SemiBold)
        Text(value.getString("note"),fontSize=12.sp,color=KoiColors.Muted)
        Text("Você pode editar ou arquivar os exemplos nas respectivas telas. A lista de identificadores fica guardada neste celular para a limpeza final.",fontSize=12.sp)
    }
}
