package com.thiago.assistentepessoal

import android.app.Application
import com.thiago.assistentepessoal.chat.*
import com.thiago.assistentepessoal.cloud.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class KoiwaiApplication : Application() {
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
    private fun database(id: String?): ChatDatabase = databases.getOrPut(id ?: "local") { ChatDatabase.open(this, id) }
    private fun activate(account: Account?) {
        val sync = account?.let { CloudSync(this, auth, it.id, database(it.id).messages()) }
        cloudSync = sync
        current.value = repos.getOrPut(account?.id ?: "local") {
            ChatRepository(database(account?.id), onSaved = { sync?.schedule() },
                tokenProvider = { account?.let { auth.token(it.id) } })
        }
        sync?.schedule()
    }
    override fun onCreate() {
        super.onCreate()
        auth = CloudAuth(SessionStore(this))
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
