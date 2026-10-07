package com.thiago.assistentepessoal

import android.app.Application
import com.thiago.assistentepessoal.chat.*
import com.thiago.assistentepessoal.cloud.*
import com.thiago.assistentepessoal.memory.MemoryRepository
import com.thiago.assistentepessoal.memory.JournalRepository
import com.thiago.assistentepessoal.memory.PeriodReports
import com.thiago.assistentepessoal.routine.TaskRepository
import com.thiago.assistentepessoal.routine.TaskReminders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class KoiwaiApplication : Application() {
    lateinit var reports:PeriodReports
        private set
    lateinit var reminders: TaskReminders
        private set
    lateinit var auth: CloudAuth
        private set
    lateinit var repositories: StateFlow<ChatRepository>
        private set
    private lateinit var current: MutableStateFlow<ChatRepository>
    val chatRepository: ChatRepository get() = current.value
    var cloudSync: CloudSync? = null
        private set
    private val databases = mutableMapOf<String, ChatDatabase>()
    private val repos = mutableMapOf<String, ChatRepository>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _memories = MutableStateFlow<MemoryRepository?>(null)
    val memories = _memories.asStateFlow()
    private val _tasks = MutableStateFlow<TaskRepository?>(null)
    val tasks = _tasks.asStateFlow()
    private val _journal = MutableStateFlow<JournalRepository?>(null)
    val journal = _journal.asStateFlow()
    private var memoryOwner: String? = null
    private var activeChatOwner: String? = "uninitialized"
    private fun database(id: String?): ChatDatabase = databases.getOrPut(id ?: "local") { ChatDatabase.open(this, id) }
    private fun activate(account: Account?) {
        if(activeChatOwner==account?.id) return
        activeChatOwner=account?.id
        reminders.account(account?.id)
        reports.account(account?.id)
        if(memoryOwner != account?.id) {
            memoryOwner = account?.id
            _memories.value?.close()
            _memories.value = account?.let { MemoryRepository(auth,it.id) }
            _tasks.value?.close()
            _journal.value?.close()
            _tasks.value = account?.let { account -> TaskRepository(auth,account.id) { tasks ->
                if(auth.account.value?.id==account.id) reminders.reconcile(account.id,tasks)
            } }
            _journal.value = account?.let { JournalRepository(auth,it.id) }
        }
        val sync = account?.let { CloudSync(this, auth, it.id, database(it.id).messages()) }
        cloudSync = sync
        current.value = repos.getOrPut(account?.id ?: "local") {
            ChatRepository(database(account?.id), onSaved = { sync?.schedule() },
                tokenProvider = { account?.let { auth.token(it.id) } },onTaskChanged={ receipt ->
                    if(auth.account.value?.id==account?.id) {
                        org.json.JSONObject(receipt).optString("task_id").takeIf {it.isNotBlank()}?.let {reminders.invalidate(it)}
                        _tasks.value?.refresh()
                    }
                })
        }
        sync?.schedule()
    }
    override fun onCreate() {
        super.onCreate()
        auth = CloudAuth(SessionStore(this))
        reminders = TaskReminders(this)
        reports = PeriodReports(this)
        current = MutableStateFlow(ChatRepository(database(null)))
        repos["local"] = current.value
        repositories = current.asStateFlow()
        activate(auth.account.value)
        scope.launch { auth.account.collect { activate(it) } }
    }
    suspend fun importLocalHistory() {
        val account = auth.account.value ?: error("Entre na sua conta primeiro.")
        val local = database(null).messages().getMessages().filter { it.status == MessageStatus.SENT }
        database(account.id).messages().merge(local.map { it.copy(sequence=0, synced=false) })
        cloudSync?.schedule()
    }
}
