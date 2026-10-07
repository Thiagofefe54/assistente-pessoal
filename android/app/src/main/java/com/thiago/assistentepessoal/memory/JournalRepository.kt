package com.thiago.assistentepessoal.memory

import com.thiago.assistentepessoal.BuildConfig
import com.thiago.assistentepessoal.chat.BackendEndpoint
import com.thiago.assistentepessoal.cloud.CloudAuth
import com.thiago.assistentepessoal.cloud.readBoundedText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class JournalItem(val text: String, val sourceIds: List<String>, val category: String = "note")
data class DayReport(val items: List<JournalItem>, val count: Int, val updatedAt: String)
data class JournalState(val day: String? = null, val busy: Boolean = false, val report: DayReport? = null,
    val stale: Boolean = false, val available: Int = 0, val suggestions: List<JournalItem> = emptyList(), val info: String? = null)

/** User-triggered generation only. No background billable inference or automatic facts. */
class JournalRepository(private val auth: CloudAuth, private val owner: String) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val _state=MutableStateFlow(JournalState())
    val state=_state.asStateFlow()
    private var job: Job? = null
    fun close() { scope.cancel() }
    suspend fun request(path: String, method: String="GET", payload: JSONObject?=null): JSONObject = withContext(Dispatchers.IO) {
        check(auth.account.value?.id==owner)
        val endpoint=BackendEndpoint.resolve(BuildConfig.BACKEND_URL,BuildConfig.DEBUG)
        if(!endpoint.authenticated) throw IOException("Resumos e sugestões precisam do servidor HTTPS.")
        val connection=URL(URL(BuildConfig.BACKEND_URL.trim().trimEnd('/')),path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.requestMethod=method
            connection.connectTimeout=10000;connection.readTimeout=90000
            connection.setRequestProperty("Authorization","Bearer ${auth.token(owner)}")
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
            if(payload!=null) {
                connection.doOutput=true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {it.write(payload.toString())}
            }
            val code=connection.responseCode
            if(code !in 200..299) {
                val raw=connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use {it.readBoundedText(2000)}
                val detail=runCatching{JSONObject(raw ?: "{}").optString("detail")}.getOrNull()
                throw IOException(if(code==401) "Entre novamente na sua conta." else detail?.takeIf {it.isNotBlank()} ?: "Não consegui acessar o diário. Atualize para conferir.")
            }
            JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use {it.readBoundedText(100000)})
        } finally {connection.disconnect()}
    }
    private fun items(rows: JSONArray)= (0 until rows.length()).map { i -> rows.getJSONObject(i).let { r ->
        val sources=r.getJSONArray("source_ids")
        JournalItem(r.getString("text"),(0 until sources.length()).map {sources.getString(it)},r.optString("category","note")) } }
    private fun applyReport(value: JSONObject) {
        val report=value.optJSONObject("report")?.let {DayReport(items(it.getJSONArray("items")),it.getInt("source_count"),it.getString("updated_at"))}
        _state.value=_state.value.copy(report=report,stale=value.optBoolean("stale"),available=value.optInt("available_count"))
    }
    private fun action(day: String, block: suspend () -> Unit) {
        if(_state.value.busy && _state.value.day==day) return
        job?.cancel()
        if(_state.value.day!=day) _state.value=JournalState(day=day)
        _state.value=_state.value.copy(busy=true,info=null)
        job=scope.launch {
            try {block()}
            catch(e: Exception) {
                if(e is CancellationException) throw e
                if(auth.account.value?.id==owner) _state.value=_state.value.copy(info=if(e is IOException) e.message else "Não consegui conferir o diário. Tente atualizar.")
            } finally {if(isActive && auth.account.value?.id==owner) _state.value=_state.value.copy(busy=false)}
        }
    }
    fun load(day: String) = action(day) {applyReport(request("/api/v1/journal/$day"))}
    fun summarize(day: String) = action(day) {
        applyReport(request("/api/v1/journal/summary","POST",JSONObject().put("local_date",day)))
        _state.value=_state.value.copy(info="Resumo salvo. Você pode conferir cada fonte. 💜")
    }
    fun suggest(day: String) = action(day) {
        val value=request("/api/v1/journal/suggestions","POST",JSONObject().put("local_date",day))
        val drafts=items(value.getJSONArray("items"))
        _state.value=_state.value.copy(suggestions=drafts,info=if(drafts.isEmpty()) "Não encontrei um detalhe duradouro para sugerir neste dia." else "São sugestões. Nada foi salvo como lembrança ainda.")
    }
    fun dismiss(item: JournalItem) {_state.value=_state.value.copy(suggestions=_state.value.suggestions-item)}
    fun delete(day: String) = action(day) {
        request("/api/v1/journal/$day","DELETE")
        _state.value=_state.value.copy(report=null,stale=false,info="Resumo apagado. As conversas continuam no diário.")
    }
}
