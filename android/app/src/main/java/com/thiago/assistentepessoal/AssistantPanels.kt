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
import com.thiago.assistentepessoal.chat.BackendEndpoint
import com.thiago.assistentepessoal.cloud.readBoundedText
import com.thiago.assistentepessoal.routine.money
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Authenticated account-scoped reads. No key, generation, persistence or redirects. */
private suspend fun readAssistant(app: KoiwaiApplication, owner: String, usage: Boolean): JSONObject {
    val endpoint = BackendEndpoint.resolve(BuildConfig.BACKEND_URL, BuildConfig.DEBUG)
    if (!endpoint.authenticated) throw IOException("Este painel precisa do servidor HTTPS.")
    val token = app.auth.token(owner) ?: throw IOException("Entre novamente na sua conta.")
    return withContext(Dispatchers.IO) {
        if (app.auth.account.value?.id != owner) throw IOException("A conta mudou. Abra o painel novamente.")
        val path = if (usage) "usage" else "day"
        val connection = URL(endpoint.url, "/api/v1/assistant/$path").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 90000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.requestMethod = if (usage) "GET" else "POST"
            if (!usage) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(JSONObject().put("timezone", ZoneId.systemDefault().id).toString())
                }
            }
            if (connection.responseCode != 200) throw IOException(when(connection.responseCode) {
                401 -> "Entre novamente na sua conta."
                404 -> "O servidor ainda está recebendo esta atualização. Tente depois."
                else -> "Não consegui atualizar agora. Confira sua conexão e tente mais tarde."
            })
            val result = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readBoundedText(100000) })
            if (app.auth.account.value?.id != owner) throw IOException("A conta mudou. Abra o painel novamente.")
            result
        } finally { connection.disconnect() }
    }
}

private fun shortDate(date: String): String = runCatching {
    java.time.LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM"))
}.getOrDefault(date)

@Composable
fun DayOverviewPanel(onRoutine: () -> Unit) {
    val app = LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val owner = account?.id
    var data by remember(owner) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(owner) { mutableStateOf(false) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var expanded by remember(owner) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    KoiPanel(Modifier.fillMaxWidth(), accent = KoiColors.Blue) {
        Eyebrow("MEU DIA", KoiColors.Blue)
        Text("Um cantinho para se organizar", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("Missões • contas • diário", color = KoiColors.Muted, fontSize = 12.sp)
        if (owner == null) Text("Entre na sua conta para consultar seu dia.", color = KoiColors.Muted)
        else {
            KoiAction(if (loading) "Conferindo seus registros…" else if (data == null) "Conferir meu dia" else "Atualizar meu dia", {
                loading = true; error = null
                scope.launch {
                    try { val result = readAssistant(app, owner, false); if(app.auth.account.value?.id==owner) data = result }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { if(app.auth.account.value?.id==owner) error = failure.message ?: "Não consegui consultar agora." }
                    finally { if(app.auth.account.value?.id==owner) loading = false }
                }
            }, Modifier.fillMaxWidth(), !loading)
            error?.let { Text(it, color = KoiColors.Red, fontSize = 12.sp) }
            data?.let { value ->
                Text("${shortDate(value.getString("date"))} • ${value.getInt("tasks_pending_today")} missões para hoje", fontWeight = FontWeight.SemiBold)
                val tasks = value.getJSONArray("tasks")
                for (index in 0 until tasks.length()) {
                    val task = tasks.getJSONObject(index)
                    Text("${task.getString("time").ifBlank { "Sem horário" }}  ·  ${task.getString("title")}", fontSize = 13.sp)
                }
                if (value.getBoolean("task_list_partial")) Text("Lista resumida; confira o restante na Rotina.", fontSize = 11.sp, color = KoiColors.Muted)
                val totals = value.getJSONObject("recorded_cents")
                HorizontalDivider(color = KoiColors.Blue.copy(alpha = .2f))
                Text("Este mês · valores registrados", color = KoiColors.Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Entrou ${money(totals.getLong("income"))}", color = KoiColors.Blue, fontSize = 13.sp)
                    Text("Saiu ${money(totals.getLong("expense"))}", color = KoiColors.Red, fontSize = 13.sp)
                }
                Text("Contas pendentes do mês: ${money(value.getLong("unpaid_month_cents"))}", fontSize = 13.sp)
                if(value.getInt("budgets_exceeded") > 0) Text("Um limite mensal foi ultrapassado. Vamos cuidar disso? 💜", color = KoiColors.Red, fontSize = 12.sp)
                TextButton(onClick = { expanded = !expanded }) { Text(if(expanded) "Recolher detalhes ↑" else "Diário e comparação ↓") }
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bills = value.getJSONArray("bills_next_seven_days")
                        Text("${value.getInt("bill_count")} contas nos próximos 7 dias", fontWeight = FontWeight.SemiBold)
                        for(index in 0 until bills.length()) {
                            val bill = bills.getJSONObject(index)
                            Text("${shortDate(bill.getString("due"))} · ${bill.getString("title")} · ${money(bill.getLong("amount_cents"))}", fontSize = 12.sp)
                        }
                        val diary = value.getJSONArray("diary_today")
                        Text("Seu diário de hoje", fontWeight = FontWeight.SemiBold)
                        if(diary.length()==0) Text("Ainda sem acontecimentos registrados hoje.", fontSize = 12.sp, color = KoiColors.Muted)
                        for(index in 0 until diary.length()) Text("✦ ${diary.getJSONObject(index).getString("title")}", fontSize = 12.sp)
                        if(!value.isNull("sleep_average_minutes")) {
                            val minutes = value.getInt("sleep_average_minutes")
                            Text("Sono na semana: média ${minutes/60}h ${minutes%60}min · ${value.getInt("sleep_complete_intervals")} intervalos completos registrados", fontSize=12.sp)
                        }
                        val comparison = value.getJSONObject("comparison")
                        Text("Comparação de despesas", fontWeight = FontWeight.SemiBold)
                        Text("${shortDate(comparison.getString("current_start"))}–${shortDate(comparison.getString("current_end"))}: ${money(comparison.getJSONObject("current").getLong("expense"))}\n${shortDate(comparison.getString("previous_start"))}–${shortDate(comparison.getString("previous_end"))}: ${money(comparison.getJSONObject("previous").getLong("expense"))}", fontSize = 12.sp)
                    }
                }
                if(value.getBoolean("records_partial")) Text("Consulta limitada aos registros mais recentes.", color = KoiColors.Red, fontSize = 11.sp)
                Text("Valores informados por você; não são saldo bancário. Atualize após alterações.", fontSize = 11.sp, color = KoiColors.Muted)
                TextButton(onClick = onRoutine) { Text("Abrir Rotina →") }
            }
            Text("Esta consulta não gera resposta de IA.", color = KoiColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun PoeUsagePanel() {
    val app = LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val owner = account?.id
    var data by remember(owner) { mutableStateOf<JSONObject?>(null) }
    var busy by remember(owner) { mutableStateOf(false) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    KoiPanel(Modifier.fillMaxWidth(), accent = KoiColors.Blue) {
        Eyebrow("CONSUMO DA IA", KoiColors.Blue)
        Text("Pontos do Poe", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        data?.let {
            Text(if(it.isNull("available_points")) "Provedor atual: ${it.getString("provider")}" else "${it.getLong("available_points")} pontos disponíveis", fontWeight=FontWeight.SemiBold)
            if(!it.isNull("checked_at")) {
                val stamp = runCatching { OffsetDateTime.parse(it.getString("checked_at")).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) }.getOrDefault("")
                Text("Consultado em $stamp", fontSize=11.sp, color=KoiColors.Muted)
            }
        }
        Text("Saldo compartilhado com a conta Poe do servidor e com o uso no site/app. Pode levar até um minuto para atualizar.", fontSize=12.sp, color=KoiColors.Muted)
        KoiAction(if(busy) "Consultando…" else "Consultar saldo", {
            if(owner!=null) {
                busy=true; error=null
                scope.launch {
                    try { val result=readAssistant(app,owner,true); if(app.auth.account.value?.id==owner) data=result }
                    catch(cancelled:CancellationException) { throw cancelled }
                    catch(failure:Exception) { if(app.auth.account.value?.id==owner) error=failure.message ?: "Não consegui consultar agora." }
                    finally { if(app.auth.account.value?.id==owner) busy=false }
                }
            }
        }, Modifier.fillMaxWidth(), owner!=null && !busy)
        error?.let { Text(it, color=KoiColors.Red, fontSize=12.sp) }
        Text(if(owner==null) "Entre na conta para consultar." else "Consulta sem gerar conversa. Sua chave permanece no servidor.", color=KoiColors.Muted, fontSize=11.sp)
    }
}
