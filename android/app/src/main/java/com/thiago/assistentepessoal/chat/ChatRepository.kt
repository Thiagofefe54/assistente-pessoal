package com.thiago.assistentepessoal.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException

// This scope belongs to the app, so leaving the chat does not interrupt an active send.
class ChatRepository(private val database: ChatDatabase, private val onSaved: () -> Unit = {},
    tokenProvider: suspend () -> String? = { null }) {
    private val dao = database.messages()
    private val backend = ChatBackend(tokenProvider)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _busy = MutableStateFlow(true)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    val messages: StateFlow<List<ChatMessage>?> = dao.observeMessages()
        .catch { _error.value = "Não consegui ler o histórico salvo no celular." }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            try {
                dao.recoverInterruptedSends()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = "Não consegui preparar o histórico. Reabra o aplicativo."
            } finally {
                _busy.value = false
            }
        }
    }

    fun send(text: String): String? {
        if (_busy.value || messages.value == null || text.isBlank() || text.trim().length > 8000) return null
        val message = ChatMessage(role = "user", content = text.trim(), status = MessageStatus.SENDING)
        perform(message, false)
        return message.id
    }

    fun retry(message: ChatMessage) {
        if (_busy.value || message.role != "user" || message.status != MessageStatus.FAILED) return
        perform(message, true)
    }

    private fun perform(message: ChatMessage, retry: Boolean) {
        _busy.value = true
        _error.value = null
        scope.launch {
            try {
                if (retry) {
                    val saved = dao.find(message.id) ?: return@launch
                    if (saved.status != MessageStatus.FAILED) return@launch
                    dao.updateStatus(message.id, MessageStatus.SENDING, null)
                } else {
                    dao.insert(message)
                }
                val context = recentChatContext(dao.recentContext(message.id, message.occurredAt), message)
                val reply = withContext(Dispatchers.IO) { backend.send(message.content, context) }
                dao.complete(message, reply)
                try { onSaved() }
                catch (scheduleError: Exception) {
                    if (scheduleError is CancellationException) throw scheduleError
                    _error.value = "Mensagem salva. Abra Minha conta para sincronizar com a nuvem."
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                try {
                    if (dao.find(message.id) != null) {
                        dao.updateStatus(message.id, MessageStatus.FAILED,
                            if (e is IOException) e.message ?: "Confira a conexão e tente novamente."
                            else "Não consegui concluir o envio. Confira a conexão e tente novamente.")
                    } else {
                        _error.value = "Não consegui salvar a mensagem. Seu texto continua no campo de entrada."
                    }
                } catch (storageError: Exception) {
                    if (storageError is CancellationException) throw storageError
                    _error.value = "Não consegui atualizar o histórico. Reabra o aplicativo antes de tentar novamente."
                }
            } finally {
                _busy.value = false
            }
        }
    }
}
