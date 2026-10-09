package com.thiago.assistentepessoal.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.thiago.assistentepessoal.*
import com.thiago.assistentepessoal.routine.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun CalendarPlanningPanel(events:List<PhoneCalendarEvent>,partial:Boolean){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState();val owner=account?.id
    var tasks by remember(owner,events){mutableStateOf<List<KoiTask>?>(null)}
    var updated by remember(owner,events){mutableStateOf<LocalTime?>(null)}
    var info by remember(owner,events){mutableStateOf<String?>(null)}
    var busy by remember(owner,events){mutableStateOf(false)}
    var day by remember(events){mutableStateOf(LocalDate.now())}
    var minutes by remember{mutableStateOf(30)};var period by remember{mutableStateOf(0)}
    var question by remember{mutableStateOf("")};var questionInfo by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val zone=ZoneId.systemDefault()
    HorizontalDivider(color=KoiColors.Blue.copy(alpha=.2f))
    Text("Encaixar meu dia",fontSize=20.sp)
    Text("Cruza agenda e tarefas ao tocar. Sem gastar pontos ou alterar horários. Hábitos sem horário ficam no Meu ritmo.",fontSize=12.sp,color=KoiColors.Muted)
    KoiAction(if(busy)"Conferindo tarefas…" else "Cruzar com minhas tarefas",{
        if(owner!=null){busy=true;info=null;scope.launch{
            try{val result=loadTasks(app.auth,owner);if(app.auth.account.value?.id==owner){tasks=result;updated=LocalTime.now()}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){if(app.auth.account.value?.id==owner){tasks=null;info="Não consegui atualizar tarefas. Confira a conexão e sua conta."}}
            finally{if(app.auth.account.value?.id==owner)busy=false}
        }}
    },enabled=owner!=null && !busy)
    if(owner==null)Text("Entre na sua conta para cruzar tarefas.",fontSize=12.sp)
    info?.let{Text(it,color=KoiColors.Red,fontSize=12.sp)}
    OutlinedTextField(question,{question=it.take(160)},label={Text("Pergunte sobre seus horários")},placeholder={Text("Estou livre amanhã à tarde?")},modifier=Modifier.fillMaxWidth())
    TextButton(onClick={
        val parsed=planningQuestion(question,LocalDate.now())
        if(parsed==null)questionInfo="Use hoje ou amanhã e, se quiser, manhã, tarde ou noite. Para os outros dias, escolha abaixo."
        else {day=parsed.first;period=parsed.second;questionInfo=if(tasks==null)"Toque em Cruzar com minhas tarefas para consultar os dados." else "Confira as janelas abaixo: consideram somente os registros consultados."}
    }){Text("Conferir horários →")}
    questionInfo?.let{Text(it,fontSize=12.sp,color=KoiColors.Muted)}
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        (0..6).forEach{offset->val date=LocalDate.now().plusDays(offset.toLong())
            FilterChip(day==date,{day=date},label={Text(if(offset==0)"Hoje" else if(offset==1)"Amanhã" else date.format(DateTimeFormatter.ofPattern("dd/MM")))})}
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
        listOf("Dia","Manhã","Tarde","Noite").forEachIndexed{i,label->FilterChip(period==i,{period=i},label={Text(label)})}
    }
    Text("Duração estimada de cada tarefa",fontSize=12.sp,color=KoiColors.Muted)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
        listOf(15,30,60).forEach{n->FilterChip(minutes==n,{minutes=n},label={Text("${n}min")})}
    }
    tasks?.let{entries->
        val hours=listOf(8 to 22,8 to 12,12 to 18,18 to 22)[period]
        val plan=calendarDayPlan(events,entries,day,zone,minutes,hours.first,hours.second)
        fun time(i:Instant)=i.atZone(zone).let{it.format(DateTimeFormatter.ofPattern(if(it.toLocalDate()==day)"HH:mm" else "dd/MM HH:mm"))}
        Text("Tarefas consultadas às ${updated?.format(DateTimeFormatter.ofPattern("HH:mm"))}. Atualize a agenda após mudanças.",fontSize=11.sp,color=KoiColors.Muted)
        if(plan.allDay.isNotEmpty())Text("Dia inteiro: ${plan.allDay.joinToString(" · ")}. Não bloqueiam horários automaticamente.",fontSize=12.sp)
        Text("Seu roteiro",color=KoiColors.Blue)
        plan.blocks.take(20).forEach{Text("${time(it.start)}–${time(it.end)} · ${it.title}\n${it.source}",fontSize=13.sp)}
        if(plan.blocks.isEmpty())Text("Nenhum compromisso com horário nos dados consultados.",fontSize=12.sp)
        Text("Possíveis conflitos",color=KoiColors.Purple)
        plan.conflicts.take(8).forEach{(a,b)->Text("${a.title} ↔ ${b.title}",fontSize=13.sp)}
        if(plan.conflicts.isEmpty())Text("Nenhum conflito encontrado nesta consulta.",fontSize=12.sp)
        Text("Janelas sem compromisso registrado",color=KoiColors.Blue)
        if(partial || plan.invalidTasks>0)Text("Dados incompletos: não consigo confirmar horários livres. Abra a agenda e revise tarefas.",color=KoiColors.Red,fontSize=12.sp)
        else {
            plan.free.take(8).forEach{Text("${time(it.start)}–${time(it.end)} · ${Duration.between(it.start,it.end).toMinutes()}min",fontSize=13.sp)}
            if(plan.free.isEmpty())Text("Não encontrei uma janela de pelo menos 15min neste período.",fontSize=12.sp)
            plan.free.firstOrNull{Duration.between(it.start,it.end).toMinutes()>=minutes}?.let{Text("Sugestão: reserve ${minutes}min a partir de ${time(it.start)} para uma pendência. Você decide o que encaixar 💜",fontSize=12.sp)}
        }
        if(plan.untimed.isNotEmpty())Text("Sem horário: ${plan.untimed.take(10).joinToString(" · ")}",fontSize=12.sp)
        Text("Repetições futuras são projeções; nenhuma tarefa é criada ou concluída. Não inclui deslocamento ou compromissos que faltam registrar. Horário sem registro não garante disponibilidade real.",fontSize=11.sp,color=KoiColors.Muted)
    }
}
