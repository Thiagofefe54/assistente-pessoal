package com.thiago.assistentepessoal.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import com.thiago.assistentepessoal.cloud.connectionMessage

// This scope belongs to the app, so leaving the chat does not interrupt an active send.
class ChatRepository(private val database: ChatDatabase, private val onSaved: () -> Unit = {},
    tokenProvider: suspend () -> String? = { null },private val onTaskChanged:(String)->Unit={},private val captureReports:()->Boolean={false}) {
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

    fun send(text: String,imageJpegBase64:String?=null): String? {
        if (_busy.value || messages.value == null || text.isBlank() || text.trim().length > 8000) return null
        if(imageJpegBase64!=null && imageJpegBase64.length>1100000)return null
        val message = ChatMessage(role = "user", content = text.trim(), status = MessageStatus.SENDING,imageJpegBase64=imageJpegBase64)
        perform(message, false)
        return message.id
    }

    fun retry(message: ChatMessage) {
        if (_busy.value || message.role != "user" || message.status != MessageStatus.FAILED) return
        perform(message, true)
    }

    fun dismissTaskDraft(id:String) { scope.launch {
        try {dao.dismissTaskDraft(id)}
        catch(e:Exception) {
            if(e is CancellationException)throw e
            _error.value="Não consegui atualizar a proposta salva. Reabra o chat para conferir."
        }
    } }
    fun undoAction(message:ChatMessage) {
        if(_busy.value || message.actionReceiptJson==null || org.json.JSONObject(message.actionReceiptJson).optString("tool") in setOf("device","bank")) return
        _busy.value=true;_error.value=null
        scope.launch {
            try {
                val id=org.json.JSONObject(message.actionReceiptJson).getString("request_id")
                val reply=withContext(Dispatchers.IO){backend.undo(id,org.json.JSONObject(message.actionReceiptJson).optString("tool")=="personal")}
                val user=ChatMessage(role="user",content="Desfaça esta ação salva.")
                dao.saveUndo(user,reply,message.id)
                scheduleSaved(message.actionReceiptJson)
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                _error.value=if(e is IOException)connectionMessage(e) else "Não consegui atualizar o resultado. Reabra a conversa para conferir."
            } finally {_busy.value=false}
        }
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
                val reply = withContext(Dispatchers.IO) {
                    (if(message.imageJpegBase64==null)com.thiago.assistentepessoal.tools.bankChatQuery(message.content) else null)?.let{query->
                        ChatResult("Claro, mestre 💜 Vou conferir ${if(query=="plan")"o saldo do Inter e suas contas cadastradas" else "o saldo do Inter"} no cartão abaixo. Os valores ficam nessa consulta privada, sem entrar no histórico enviado à IA.",
                            actionReceiptJson=org.json.JSONObject().put("tool","bank").put("type","read").put("query",query).toString())
                    } ?: (if(message.imageJpegBase64==null)com.thiago.assistentepessoal.tools.calculationReply(message.content) else null)?.let{ChatResult(it)}
                        ?: backend.sendResult(message.content, context,message.id,message.timezone,message.occurredAt,message.imageJpegBase64,captureReports())
                }
                dao.complete(message, reply.reply, reply.taskDraftJson,reply.actionReceiptJson)
                scheduleSaved(reply.actionReceiptJson)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                try {
                    if (dao.find(message.id) != null) {
                        dao.updateStatus(message.id, MessageStatus.FAILED,
                            if (e is IOException) connectionMessage(e)
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

    private fun scheduleSaved(receipt:String?) {
        try {if(receipt!=null) onTaskChanged(receipt);onSaved()}
        catch(e:Exception) {
            if(e is CancellationException)throw e
            _error.value="Resultado salvo. Atualize a Rotina e sincronize em Minha conta para conferir."
        }
    }
}
