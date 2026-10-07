package com.thiago.assistentepessoal.routine

import com.thiago.assistentepessoal.cloud.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import java.net.URLEncoder
import java.time.*
import java.util.UUID

data class KoiTask(val id: String, val slot: Int, val title: String, val notes: String,
    val date: String?, val time: String?, val timezone: String, val recurrence: String,
    val completedAt: String?, val count: Int, val updatedAt: String) {
    fun today(now: Instant = Instant.now()): Boolean = completedAt==null && date!=null &&
        runCatching {date==now.atZone(ZoneId.of(timezone)).toLocalDate().toString()}.getOrDefault(false)
    fun overdue(now: Instant = Instant.now()): Boolean = completedAt == null && date != null &&
        runCatching {
            val current=now.atZone(ZoneId.of(timezone))
            val due=LocalDate.parse(date)
            due < current.toLocalDate() || (due==current.toLocalDate() && time!=null &&
                !current.toLocalTime().isBefore(LocalTime.parse(time)))
        }.getOrDefault(false)
}
val taskRecurrences = linkedMapOf("none" to "Uma vez", "daily" to "Diária", "weekly" to "Semanal", "monthly" to "Mensal")
data class TaskDraft(val title: String, val notes: String, val date: String, val time: String, val recurrence: String) {
    fun error(): String? = when {
        title.trim().isEmpty() || title.trim().length > 160 -> "Escreva um título de até 160 caracteres."
        notes.length > 2000 -> "A descrição pode ter até 2 mil caracteres."
        recurrence !in taskRecurrences -> "Escolha uma repetição válida."
        date.isNotBlank() && runCatching { LocalDate.parse(date) }.isFailure -> "Use uma data válida: AAAA-MM-DD."
        time.isNotBlank() && !Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(time) -> "Use um horário válido: HH:mm."
        date.isBlank() && (time.isNotBlank() || recurrence != "none") -> "Escolha uma data para usar horário ou repetição."
        else -> null
    }
}
class TaskRepository(private val auth: CloudAuth, private val owner: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _tasks = MutableStateFlow<List<KoiTask>?>(null)
    val tasks = _tasks.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _info = MutableStateFlow<String?>(null)
    val info = _info.asStateFlow()
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent = _recent.asStateFlow()
    fun clearInfo() { _info.value=null }
    fun close() { scope.cancel() }
    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): String {
        check(auth.account.value?.id == owner)
        return withContext(Dispatchers.IO) { CloudApi.request(path,method,body?.toString(),auth.token(owner),"return=representation") }
    }
    private fun nullable(row: JSONObject, key: String) = if(row.isNull(key)) null else row.getString(key)
    private suspend fun fetch() {
        val rows = JSONArray(request("/rest/v1/koi_tasks?select=*&user_id=eq.$owner&order=created_at.asc&limit=500"))
        val entries = (0 until rows.length()).map { i -> rows.getJSONObject(i).let { r ->
            KoiTask(r.getString("id"),r.getInt("slot"),r.getString("title"),r.getString("notes"),
                nullable(r,"due_date"),nullable(r,"due_time"),r.getString("timezone"),r.getString("recurrence"),
                nullable(r,"completed_at"),r.getInt("completed_count"),r.getString("updated_at")) } }
        val completed = JSONArray(request("/rest/v1/koi_task_completions?select=title,completed_at&user_id=eq.$owner&order=completed_at.desc&limit=10"))
        if(auth.account.value?.id == owner) {
            _tasks.value = entries
            _recent.value = (0 until completed.length()).map { i -> completed.getJSONObject(i).let {
                val day = Instant.parse(it.getString("completed_at")).atZone(ZoneId.systemDefault()).toLocalDate()
                "$day • ${it.getString("title")}" } }
        }
    }
    private fun action(block: suspend () -> Unit) {
        if(_busy.value) return
        _busy.value = true; _info.value = null
        scope.launch {
            try { block() }
            catch(e: Exception) {
                if(e is CancellationException) throw e
                _info.value = when {
                    auth.account.value?.id != owner -> "A conta mudou. Reabra suas tarefas."
                    e is CloudException && e.code == 409 -> "Suas tarefas mudaram. Atualize para conferir."
                    e is CloudException && e.code in listOf(401,403) -> "Entre novamente na sua conta."
                    else -> "Não consegui confirmar a operação. Atualize antes de tentar novamente."
                }
            } finally { _busy.value = false }
        }
    }
    fun refresh() = action { fetch() }
    private fun filter(task: KoiTask) = "/rest/v1/koi_tasks?user_id=eq.$owner&id=eq.${task.id}&updated_at=eq." + URLEncoder.encode(task.updatedAt,"UTF-8")
    private suspend fun afterMutation(success: String) {
        try {fetch();_info.value=success}
        catch(e: Exception) {
            if(e is CancellationException) throw e
            _info.value="$success A lista ainda não atualizou. Toque em Atualizar tarefas."
        }
    }
    fun save(draft: TaskDraft, existing: KoiTask? = null, creationId: String? = null, onSaved: (() -> Unit)? = null) {
        if(draft.error()!=null) return
        action {
            val body=JSONObject().put("title",draft.title.trim()).put("notes",draft.notes.trim())
                .put("due_date",draft.date.ifBlank { null } ?: JSONObject.NULL)
                .put("due_time",draft.time.ifBlank { null } ?: JSONObject.NULL)
                .put("timezone",existing?.timezone ?: ZoneId.systemDefault().id).put("recurrence",draft.recurrence)
            if(existing==null) {
                fetch()
                // Keep the same identity when a chat proposal is retried after
                // an ambiguous connection failure. Never overwrite another task.
                val id=creationId ?: UUID.randomUUID().toString()
                UUID.fromString(id)
                if(_tasks.value.orEmpty().any {it.id==id}) {
                    _info.value="Esta proposta já foi salva. Confira em Rotina. 💜"
                    onSaved?.invoke();return@action
                }
                val slot=(1..500).firstOrNull { n -> _tasks.value.orEmpty().none { it.slot==n } }
                    ?: run { _info.value="Você chegou a 500 tarefas. Revise as antigas para liberar espaço."; return@action }
                body.put("id",id).put("user_id",owner).put("slot",slot)
                check(JSONArray(request("/rest/v1/koi_tasks","POST",body)).length()==1)
            } else {
                // Changing recurrence of a finished task explicitly reopens it.
                if(draft.recurrence!="none") body.put("completed_at",JSONObject.NULL)
                if(JSONArray(request(filter(existing),"PATCH",body)).length()!=1) {
                    fetch(); _info.value="Esta tarefa mudou. Abra a versão atual antes de editar."; return@action
                }
            }
            onSaved?.invoke()
            afterMutation("Missão salva na sua conta. 💜")
        }
    }
    fun complete(task: KoiTask) = action {
        val done=request("/rest/v1/rpc/complete_koi_task","POST",JSONObject()
            .put("task_id",task.id).put("expected_updated_at",task.updatedAt)).trim()=="true"
        afterMutation(if(!done) "Esta tarefa já mudou. Confira a versão atual."
            else if(task.recurrence=="none") "Missão concluída! 💜" else "Etapa concluída! A próxima data está pronta. 💜")
    }
    fun reopen(task: KoiTask) = action {
        val changed=JSONArray(request(filter(task),"PATCH",JSONObject().put("completed_at",JSONObject.NULL))).length()==1
        afterMutation(if(changed) "Tarefa reaberta. A conclusão anterior continua registrada." else "Esta tarefa mudou. Confira a versão atual.")
    }
    fun delete(task: KoiTask) = action {
        val changed=JSONArray(request(filter(task),"DELETE")).length()==1
        afterMutation(if(changed) "Tarefa e seus registros de conclusão apagados." else "Esta tarefa mudou. Confira antes de apagar.")
    }
}
