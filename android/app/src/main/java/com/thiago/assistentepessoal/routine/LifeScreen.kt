package com.thiago.assistentepessoal.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import org.json.JSONObject
import java.time.*
import java.util.UUID

@Composable
fun LifeScreen(category:String,onBack:()->Unit,onAccount:()->Unit){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val repository by app.personal.collectAsState()
    val repo=repository
    if(repo==null){Column(Modifier.padding(22.dp)){TextButton(onClick=onBack){Text("← Rotina")};Text(category,fontSize=30.sp);Text("Entre para guardar seus registros.");KoiAction("Minha conta",onAccount)};return}
    val records by repo.records.collectAsState();val payments by repo.payments.collectAsState();val busy by repo.busy.collectAsState();val info by repo.info.collectAsState();val undo by repo.undo.collectAsState()
    val today=LocalDate.now();val month=today.withDayOfMonth(1)
    var billMonthText by rememberSaveable{mutableStateOf(month.toString())}
    val billMonth=LocalDate.parse(billMonthText)
    val kind=when(category){"Contas"->"bill";"Orçamento"->"budget";else->"diary"}
    val accent=if(kind=="diary")KoiColors.Purple else KoiColors.Blue
    var editor by remember{mutableStateOf(false)};var editing by remember{mutableStateOf<PersonalRecord?>(null)}
    var creationId by rememberSaveable{mutableStateOf(UUID.randomUUID().toString())}
    var archived by rememberSaveable{mutableStateOf(false)}
    var filter by rememberSaveable{mutableStateOf("all")}
    val all=records.orEmpty();val own=all.filter{it.kind==kind && it.archived==archived}
    val spent=monthlySpent(all,today)
    LaunchedEffect(repo){repo.refresh()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            TextButton(onClick=onBack){Text("← Rotina")};Eyebrow("SEU DIA COM A KOI",accent);Text(category,fontSize=32.sp)
            Text(when(kind){"bill"->"Vencimentos previstos. Marcar pago registra uma despesa; não movimenta sua conta bancária.";"budget"->"Limite do mês comparado às despesas registradas. Não é saldo bancário.";else->"Acontecimentos por dia, sem inventar o que você não contou."},color=KoiColors.Muted)
            Spacer(Modifier.height(10.dp));KoiAction("＋ ${if(kind=="bill")"Nova conta" else if(kind=="budget")"Definir limite" else "Novo acontecimento"}",{editing=null;creationId=UUID.randomUUID().toString();editor=true},enabled=!busy && records!=null && all.size<2000)
            Row{FilterChip(selected=!archived,onClick={archived=false},label={Text("Ativos")});Spacer(Modifier.width(8.dp));FilterChip(selected=archived,onClick={archived=true},label={Text("Arquivados")})}
            TextButton(onClick={repo.refresh()},enabled=!busy){Text("Atualizar")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            info?.let{Text(it,color=KoiColors.Blue)}
            if(undo!=null)TextButton(onClick={repo.undoLast()},enabled=!busy){Text("↶ Desfazer última ação")}
        }
        if(kind=="bill" && !archived){
            val occurrences=billOccurrences(all,payments,billMonth,billMonth.plusMonths(1))
            item{KoiPanel(Modifier.fillMaxWidth(),accent=accent){Row{TextButton(onClick={billMonthText=billMonth.minusMonths(1).toString()}){Text("←")};Text("${billMonth.monthValue}/${billMonth.year}",fontSize=22.sp);TextButton(onClick={billMonthText=billMonth.plusMonths(1).toString()}){Text("→")}};Text("Falta pagar: ${money(occurrences.filter{!it.paid}.sumOf{it.record.cents ?: 0})}");Text("${occurrences.count{it.paid}} de ${occurrences.size} vencimentos pagos. Navegue para conferir outros meses.",color=KoiColors.Muted)}}
            items(occurrences,key={"${it.record.id}:${it.date}"}){item->KoiPanel(Modifier.fillMaxWidth(),accent=if(item.paid)KoiColors.Purple else accent){
                Eyebrow(if(item.paid)"PAGA" else if(item.date<today)"VENCIDA" else "A PAGAR",accent)
                Text(item.record.title,fontSize=22.sp);Text("${money(item.record.cents ?: 0)} · ${item.date}")
                if(!item.paid)KoiAction("Já paguei",{repo.pay(item.record,item.date.toString())},enabled=!busy)
            }}
        }
        if(kind=="budget")item{KoiPanel(Modifier.fillMaxWidth(),accent=accent){Text("Gastos registrados neste mês",fontSize=20.sp);Text(money(spent),fontSize=27.sp);Text("Entradas e contas previstas não são despesas. Pagamentos registrados entram no total.",color=KoiColors.Muted)}}
        if(kind=="diary"){
            item{WeeklyLifePanel(all,today)}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){FilterChip(selected=filter=="all",onClick={filter="all"},label={Text("Todos")});FilterChip(selected=filter=="week",onClick={filter="week"},label={Text("Semana")});FilterChip(selected=filter=="sleep",onClick={filter="sleep"},label={Text("Sono")})}}
            val monday=today.minusDays((today.dayOfWeek.value-1).toLong())
            val entries=own.filter{filter=="all" || (filter=="week" && it.date!=null && it.date>=monday.toString() && it.date<=today.toString()) || (filter=="sleep" && JSONObject(it.details).optString("category")=="sleep")}.sortedByDescending{it.date}
            entries.groupBy{it.date ?: "Sem data"}.forEach{(day,rows)->
                item(key="day:$day"){Eyebrow(day,accent)}
                items(rows,key={it.id}){r->LifeRecordCard(r,busy,accent,monthlySpent(all,today,financeCategory(r)),month,{editing=r;editor=true},{repo.archive(r)})}
            }
            if(entries.isEmpty())item{Text("Nada neste filtro ainda. Conte seu dia no chat com a captura ativada ou registre aqui.",color=KoiColors.Muted)}
        }else{
            item{Text(if(kind=="bill")"Contas cadastradas · editar ou arquivar" else "Limites por mês",fontSize=19.sp)}
            items(own.sortedByDescending{it.date},key={it.id}){r->LifeRecordCard(r,busy,accent,monthlySpent(all,today,financeCategory(r)),month,{editing=r;editor=true},{repo.archive(r)})}
            if(own.isEmpty())item{Text("Nenhum registro neste filtro.",color=KoiColors.Muted)}
        }
    }
    if(editor)key(creationId,editing?.id){
        val initial=JSONObject(editing?.details ?: "{}")
        var title by rememberSaveable{mutableStateOf(editing?.title ?: "")};var content by rememberSaveable{mutableStateOf(editing?.content ?: "")}
        var amount by rememberSaveable{mutableStateOf(editing?.cents?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()} ?: "")}
        var day by rememberSaveable{mutableStateOf(editing?.date ?: (if(kind=="budget")month else today).toString())}
        var finance by rememberSaveable{mutableStateOf(editing?.let{financeCategory(it)} ?: "all")}
        var repeat by rememberSaveable{mutableStateOf(initial.optString("repeat","none"))}
        var event by rememberSaveable{mutableStateOf(initial.optString("category","other"))}
        var start by rememberSaveable{mutableStateOf(initial.optString("started_at",""))};var end by rememberSaveable{mutableStateOf(initial.optString("ended_at",""))}
        val valid=title.isNotBlank() && title.length<=160 && content.length<=8000 && runCatching{LocalDate.parse(day)}.isSuccess && (kind=="diary" || parseCents(amount)!=null) &&
            (start.isBlank() || runCatching{OffsetDateTime.parse(start).toLocalDate().toString()==day}.getOrDefault(false)) && (end.isBlank() || sleepDuration(start,end)!=null)
        AlertDialog(onDismissRequest={if(!busy)editor=false},title={Text(if(editing==null)"Novo registro" else "Editar")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(title,{title=it},label={Text("Título")});OutlinedTextField(content,{content=it},label={Text("Detalhes")},minLines=2)
            if(kind!="diary")OutlinedTextField(amount,{amount=it},label={Text("Valor em reais")},singleLine=true)
            OutlinedTextField(day,{day=it},label={Text(if(kind=="bill")"Primeiro vencimento: AAAA-MM-DD" else if(kind=="budget")"Mês: use AAAA-MM-01" else "Data: AAAA-MM-DD")},singleLine=true)
            if(kind=="budget")FinanceCategoryPicker(finance,{finance=it},true)
            if(kind=="bill")billRepeats.forEach{(id,label)->FilterChip(selected=repeat==id,onClick={repeat=id},label={Text(label)})}
            if(kind=="diary"){
                diaryCategories.forEach{(id,label)->FilterChip(selected=event==id,onClick={event=id},label={Text(label)})}
                Text("Horários opcionais, com data e fuso. Ex.: 2026-10-08T23:00:00-03:00. Para sono, o fim pode ser no dia seguinte.",fontSize=12.sp,color=KoiColors.Muted)
                OutlinedTextField(start,{start=it},label={Text("Início opcional")});OutlinedTextField(end,{end=it},label={Text("Fim opcional")})
            }
            info?.let{Text(it,color=KoiColors.Blue)}
        }},confirmButton={TextButton(enabled=valid && !busy,onClick={
            val details=JSONObject();if(kind=="budget")details.put("finance_category",finance);if(kind=="bill")details.put("repeat",repeat)
            if(kind=="diary"){details.put("category",event);if(start.isNotBlank())details.put("started_at",start);if(end.isNotBlank())details.put("ended_at",end)}
            repo.save(kind,title,content,if(kind=="diary")null else parseCents(amount),0,if(kind=="budget")LocalDate.parse(day).withDayOfMonth(1).toString() else day,editing,creationId,details){editor=false}
        }){Text(if(busy)"Salvando…" else "Salvar")}},dismissButton={TextButton(onClick={editor=false},enabled=!busy){Text("Cancelar")}})
    }
}

@Composable private fun WeeklyLifePanel(records:List<PersonalRecord>,today:LocalDate){
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val repository by app.tasks.collectAsState();val tasks by (repository?.tasks ?: remember{ kotlinx.coroutines.flow.MutableStateFlow<List<KoiTask>?>(null) }).collectAsState()
    LaunchedEffect(repository){repository?.refresh()}
    val start=today.minusDays((today.dayOfWeek.value-1).toLong());val end=today.plusDays(1)
    val rows=records.filter{!it.archived && it.date!=null && it.date>=start.toString() && it.date<end.toString()}
    val diary=rows.filter{it.kind=="diary"}
    val completed=tasks.orEmpty().count{it.archivedAt==null && it.completedAt!=null && runCatching{OffsetDateTime.parse(it.completedAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() in start..today}.getOrDefault(false)}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
        Eyebrow("SEU BALANÇO DA SEMANA",KoiColors.Purple);Text("$start → $today",fontSize=18.sp)
        Text("${diary.size} acontecimentos · ${money(rows.filter{it.kind=="expense"}.sumOf{it.cents ?: 0})} em gastos")
        if(tasks!=null)Text("$completed tarefas concluídas · ${tasks.orEmpty().count{it.archivedAt==null && it.completedAt==null}} pendentes no total")
        diary.groupingBy{JSONObject(it.details).optString("category","other")}.eachCount().forEach{(type,count)->Text("${diaryCategories[type] ?: "Outros"}: $count")}
        val durations=diary.filter{JSONObject(it.details).optString("category")=="sleep"}.mapNotNull{r->val d=JSONObject(r.details);sleepDuration(d.optString("started_at"),d.optString("ended_at"))}
        if(durations.isNotEmpty()){val average=durations.sum()/durations.size;Text("Sono: média ${average/60}h ${average%60}min em ${durations.size} intervalos completos")}
        Text("Só o que foi registrado. Dias sem relato e sono sem dois horários não entram nos cálculos. Relatórios narrativos continuam em Memória → Relatórios.",color=KoiColors.Muted,fontSize=12.sp)
    }
}

@Composable
private fun LifeRecordCard(r:PersonalRecord,busy:Boolean,accent:androidx.compose.ui.graphics.Color,spent:Long,month:LocalDate,onEdit:()->Unit,onArchive:()->Unit){
    KoiPanel(Modifier.fillMaxWidth(),accent=accent){
        Text(r.title,fontSize=21.sp);Text(r.date ?: "",color=KoiColors.Muted)
        if(r.content.isNotBlank())Text(r.content)
        val details=JSONObject(r.details)
        when(r.kind){
            "bill"->{Text(money(r.cents ?: 0));Text(billRepeats[details.optString("repeat","none")] ?: "Uma vez",color=KoiColors.Muted)}
            "budget"->{Text(financeLabel(financeCategory(r)),color=accent);val limit=r.cents ?: 0;Text("Limite: ${money(limit)}");if(r.date==month.toString() && !r.archived){Text(if(spent>limit)"Limite ultrapassado em ${money(spent-limit)}" else "Restam ${money(limit-spent)} até o limite",color=if(spent>limit)KoiColors.Red else accent);LinearProgressIndicator(progress={if(limit==0L)if(spent>0)1f else 0f else (spent.toDouble()/limit).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())}}
            "diary"->{Eyebrow(diaryCategories[details.optString("category","other")] ?: "Outros",accent);if(details.has("started_at"))Text(details.getString("started_at"),fontSize=12.sp);if(details.has("ended_at"))Text(details.getString("ended_at"),fontSize=12.sp);if(details.optString("category")=="sleep")sleepDuration(details.optString("started_at"),details.optString("ended_at"))?.let{Text("Sono registrado: ${it/60}h ${it%60}min")}}
        }
        Row{TextButton(onClick=onEdit,enabled=!busy){Text("Editar")};TextButton(onClick=onArchive,enabled=!busy){Text(if(r.archived)"Recuperar" else "Arquivar")}}
    }
}
