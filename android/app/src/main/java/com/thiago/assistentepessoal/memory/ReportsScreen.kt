package com.thiago.assistentepessoal.memory

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.LocalDate

@Composable fun ReportsScreen(onBack:()->Unit,onDay:(String)->Unit,initialKind:String="week",initialAnchor:String=LocalDate.now().toString()) {
    val context=LocalContext.current;val app=context.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState();val api by app.journal.collectAsState()
    var kind by rememberSaveable {mutableStateOf(initialKind.takeIf {it in reportKinds} ?: "week")}
    var anchor by rememberSaveable {mutableStateOf(runCatching {LocalDate.parse(initialAnchor).toString()}.getOrDefault(LocalDate.now().toString()))}
    var value by remember {mutableStateOf<JSONObject?>(null)}
    var busy by remember {mutableStateOf(false)};var info by remember {mutableStateOf<String?>(null)}
    var progress by remember {mutableStateOf<String?>(null)}
    var refresh by remember {mutableIntStateOf(0)};var deleting by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(account?.id,api,kind,anchor,refresh) {
        value=null;info=null
        if(account!=null && api!=null) {busy=true;try {value=api!!.request("/api/v1/reports/read","POST",JSONObject().put("kind",kind).put("anchor",anchor).put("timezone",java.time.ZoneId.systemDefault().id))}
            catch(e:Exception) {if(e is CancellationException)throw e;info=e.message}
            finally {busy=false}}
    }
    LaunchedEffect(account?.id,kind,anchor) {while(true) {progress=account?.id?.let {app.reports.status(it,kind,anchor)};delay(1000)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(onClick=onBack){Text("← Memória")}
            Eyebrow("SUA HISTÓRIA EM PERSPECTIVA",KoiColors.Blue)
            Text("Pequenos dias.\nUma grande história.",fontSize=30.sp,lineHeight=36.sp)
            Text("Relatórios com fontes e o período realmente registrado. Nenhum mês anterior ao seu diário será inventado.",color=KoiColors.Muted)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                reportKinds.forEach {(id,label)->FilterChip(selected=kind==id,onClick={kind=id},label={Text(label)})}
            }
            Row {
                TextButton(onClick={val d=LocalDate.parse(anchor);anchor=(when(kind){"week"->d.minusWeeks(1);"month"->d.minusMonths(1);"halfyear"->d.minusMonths(6);else->d.minusYears(1)}).toString()}){Text("← Anterior")}
                TextButton(onClick={val d=LocalDate.parse(anchor);DatePickerDialog(context,{_,y,m,day->anchor=LocalDate.of(y,m+1,day).toString()},d.year,d.monthValue-1,d.dayOfMonth).show()}){Text("Escolher data")}
                TextButton(onClick={val d=LocalDate.parse(anchor);anchor=(when(kind){"week"->d.plusWeeks(1);"month"->d.plusMonths(1);"halfyear"->d.plusMonths(6);else->d.plusYears(1)}).toString()}){Text("Próximo →")}
            }
            Text("Data de referência: $anchor",color=KoiColors.Blue)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            info?.let {Text(it,color=KoiColors.Red)}
            progress?.let {Text(it,color=KoiColors.Blue)}
            if(account==null) Text("Entre na sua conta para consultar os relatórios.")
            else {
                KoiAction("✦ Preparar / continuar relatório",{app.reports.prepare(account!!.id,kind,anchor)},Modifier.fillMaxWidth(),!busy)
                Row {
                    TextButton(onClick={refresh++},enabled=!busy){Text("Atualizar")}
                    TextButton(onClick={app.reports.cancel(account!!.id,kind,anchor)}){Text("Interromper preparo")}
                }
            }
        }
        value?.let { state ->
            item {
                KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
                    Text("${state.getString("start")} até ${LocalDate.parse(state.getString("end")).minusDays(1)}")
                    Text("${state.optInt("available_count")} mensagens suas sincronizadas",color=KoiColors.Muted)
                    if(state.optBoolean("open")) Text("Período em andamento: esta versão é parcial.",color=KoiColors.Blue)
                    if(state.optBoolean("stale")) Text("Há registros novos. Continue a preparação para atualizar.",color=KoiColors.Red)
                    if(state.optInt("remaining")>0) Text("Vou preparar os capítulos necessários antes de reunir tudo. Você pode sair desta tela.",color=KoiColors.Muted)
                    val report=state.optJSONObject("report")
                    if(report==null) Text("Seu relatório aparecerá aqui quando estiver pronto.")
                    else {
                        val coverage=report.getJSONObject("coverage")
                        Text("Fontes: ${coverage.getString("first_day")} a ${coverage.getString("last_day")} • ${coverage.getInt("days_with_messages")} dias registrados",color=KoiColors.Blue)
                        Text("Síntese por IA dos relatos no chat. Confira os capítulos; não representa atividades fora do diário.",color=KoiColors.Muted)
                        val items=report.getJSONArray("items")
                        for(i in 0 until items.length()) {
                            val entry=items.getJSONObject(i);Text("• ${entry.getString("text")}")
                            val keys=entry.getJSONArray("source_keys")
                            for(j in 0 until keys.length()) {val key=keys.getString(j);val parts=key.split(':',limit=2)
                                TextButton(onClick={if(parts[0]=="day") onDay(parts[1]) else {kind=parts[0];anchor=parts[1]}}) {Text("Ver ${if(parts[0]=="day")"dia" else "mês"} ${parts[1]}")}
                            }
                        }
                        TextButton(onClick={deleting=true},enabled=!busy){Text("Apagar este relatório",color=KoiColors.Red)}
                    }
                }
            }
        }
    }
    if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("Apagar este relatório?")},text={Text("Os capítulos, conversas e lembranças continuam salvos. Este celular não vai recriar o relatório automaticamente; você poderá pedir de novo.")},
        confirmButton={TextButton(onClick={deleting=false;scope.launch {
            busy=true;try {account?.id?.let {app.reports.suppress(it,kind,anchor)};api?.request("/api/v1/reports/$kind/$anchor","DELETE");refresh++}
            catch(e:Exception){if(e is CancellationException)throw e;info=e.message} finally {busy=false}
        }}){Text("Apagar")}},dismissButton={TextButton(onClick={deleting=false}){Text("Cancelar")}})
}

@Composable fun ReportSettings() {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    var enabled by remember {mutableStateOf(app.reports.automatic())}
    var notify by remember {mutableStateOf(app.reports.prefs.getBoolean("notify",true))}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple) {
        Text("Sua história, reunida pela Koi",style=MaterialTheme.typography.titleMedium)
        var limit by remember{mutableIntStateOf(app.reports.prefs.getInt("auto-limit",3))}
        Text("Limite de pedidos automáticos por dia: $limit (pode consumir pontos de IA). Preparo manual é separado.")
        Row {listOf(1,3,5).forEach{n->TextButton(onClick={limit=n;app.reports.prefs.edit().putInt("auto-limit",n).apply()}){Text("$n/dia")}}}
        Row {Text("Preparar relatórios automaticamente",Modifier.weight(1f));Switch(enabled=account!=null,checked=enabled,onCheckedChange={enabled=it;app.reports.setAutomatic(it)})}
        Text("Ao ativar, a Koi envia capítulos sincronizados à IA para preparar dia, semana, mês, semestre e ano encerrados. Trabalha aos poucos, com internet e quando o Android permitir. Não contrata outro serviço; usa os pontos da IA já configurada.",color=KoiColors.Muted)
        Row {Text("Avisar quando estiverem prontos",Modifier.weight(1f));Switch(checked=notify,onCheckedChange={notify=it;app.reports.prefs.edit().putBoolean("notify",it).apply()})}
        if(notify && !app.reminders.allowed()) Text("Permita notificações no cartão de lembretes para receber os avisos.",color=KoiColors.Blue)
        Text("Respeita o descanso configurado. O celular desligado ou sem rede adia o preparo; não depende do seu PC. Para ler, abra Memória → Relatórios.",color=KoiColors.Muted)
    }
}
