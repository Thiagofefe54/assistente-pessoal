package com.thiago.assistentepessoal.memory

import com.thiago.assistentepessoal.cloud.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

data class MemoryFact(val id: String, val slot: Int, val content: String, val category: String,
    val sourceId: String?, val updatedAt: String)

val memoryCategories = linkedMapOf("preference" to "Preferência", "goal" to "Objetivo",
    "routine" to "Rotina", "note" to "Sobre mim")

/** Online mutations only; no stale offline queue can resurrect a deleted fact. */
class MemoryRepository(private val auth: CloudAuth, private val owner: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val _facts = MutableStateFlow<List<MemoryFact>?>(null)
    val facts = _facts.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _info = MutableStateFlow<String?>(null)
    val info = _info.asStateFlow()

    private suspend fun request(path: String, method: String = "GET", body: String? = null): String {
        check(auth.account.value?.id == owner) { "A conta mudou. Reabra Memória." }
        return withContext(Dispatchers.IO) {
            CloudApi.request(path, method, body, auth.token(owner), "return=representation")
        }
    }
    private suspend fun fetch() {
        val rows = JSONArray(request("/rest/v1/memory_facts?select=*&user_id=eq.$owner&order=slot.asc&limit=20"))
        val items = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            MemoryFact(row.getString("id"), row.getInt("slot"), row.getString("content"),
                row.getString("category"), if(row.isNull("source_message_id")) null else row.getString("source_message_id"),
                row.getString("updated_at"))
        }
        // Do not publish the previous account's result if logout happened in flight.
        if(auth.account.value?.id == owner) _facts.value = items
    }
    private fun action(block: suspend () -> Unit) {
        if(_busy.value) return
        _busy.value = true
        _info.value = null
        scope.launch {
            try { mutex.withLock { block() } }
            catch(e: Exception) {
                if(e is CancellationException) throw e
                _info.value = when {
                    auth.account.value?.id != owner -> "A conta mudou. Reabra Memória."
                    e is CloudException && e.code == 409 -> "A memória mudou em outro dispositivo. Atualize e tente novamente."
                    e is CloudException && e.code in listOf(401,403) -> "Entre novamente na sua conta."
                    else -> "Não consegui confirmar a operação na nuvem. Atualize para conferir antes de tentar novamente."
                }
            } finally { _busy.value = false }
        }
    }
    fun refresh() = action { fetch() }
    fun save(content: String, category: String, sourceId: String? = null, existing: MemoryFact? = null) {
        val clean = content.trim()
        if(clean.isEmpty() || clean.length > 500 || category !in memoryCategories) return
        action {
            if(existing == null) {
                fetch()
                val slot = (1..20).firstOrNull { candidate -> _facts.value.orEmpty().none { it.slot == candidate } }
                    ?: run { _info.value = "Você já tem 20 lembranças. Revise uma antes de adicionar outra."; return@action }
                val body = JSONObject().put("user_id",owner).put("id",UUID.randomUUID().toString())
                    .put("slot",slot).put("content",clean).put("category",category)
                    .put("source_message_id",sourceId ?: JSONObject.NULL)
                request("/rest/v1/memory_facts", "POST", body.toString())
            } else {
                val rows = JSONArray(request(filter(existing), "PATCH",
                    JSONObject().put("content",clean).put("category",category).toString()))
                if(rows.length() != 1) { fetch(); _info.value = "Essa lembrança mudou. Abra a versão atual para editar."; return@action }
            }
            fetch()
            _info.value = "Lembrança confirmada. A Koi poderá usá-la nas próximas respostas."
        }
    }
    private fun filter(fact: MemoryFact): String = "/rest/v1/memory_facts?user_id=eq.$owner&id=eq.${fact.id}&updated_at=eq." +
        URLEncoder.encode(fact.updatedAt,"UTF-8")
    fun delete(fact: MemoryFact) = action {
        val rows = JSONArray(request(filter(fact), "DELETE"))
        fetch()
        _info.value = if(rows.length() == 1) "Lembrança apagada. A conversa original continua no diário."
            else "Essa lembrança mudou. Confira a versão atual antes de apagar."
    }
}
