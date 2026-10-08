package com.thiago.assistentepessoal.chat

import com.thiago.assistentepessoal.BuildConfig
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.thiago.assistentepessoal.cloud.readBoundedText

class ChatBackend(private val tokenProvider: suspend () -> String? = { null }) {
    suspend fun send(message: String, history: List<ChatContextEntry> = emptyList()): String = sendResult(message,history).reply
    suspend fun sendResult(message: String, history: List<ChatContextEntry> = emptyList(), requestId:String?=null, timezone:String=java.time.ZoneId.systemDefault().id,requestedAt:Long=System.currentTimeMillis(),imageJpegBase64:String?=null,captureReports:Boolean=false): ChatResult {
        val endpoint = BackendEndpoint.resolve(BuildConfig.BACKEND_URL, BuildConfig.DEBUG)
        // Never even retrieve credentials for the HTTP development simulator.
        val token = if (endpoint.authenticated) tokenProvider()
            ?: throw IOException("Entre na sua conta para conversar.") else null
        val connection = endpoint.url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 90000
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                val payload = JSONObject().put("message", message)
                // The HTTP development simulator never receives personal history.
                if (endpoint.authenticated) {
                    payload.put("contextual_mode",true).put("capture_reports",captureReports)
                    payload.put("timezone",timezone)
                    payload.put("requested_at",java.time.Instant.ofEpochMilli(requestedAt).toString())
                    imageJpegBase64?.let{payload.put("image_jpeg_base64",it)}
                    if(requestId!=null) payload.put("request_id",requestId).put("task_mode","direct")
                    payload.put("history", JSONArray().apply {
                        history.forEach { item -> put(JSONObject().put("role", item.role).put("content", item.content)) }
                    })
                }
                it.write(payload.toString())
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val explanation = when (connection.responseCode) {
                    401 -> "Entre novamente na sua conta para conversar."
                    429 -> "A IA atingiu o limite de uso. Aguarde e tente novamente."
                    409 -> "A tarefa mudou. Atualize sua rotina antes de tentar novamente."
                    502 -> "Não consegui preparar uma resposta válida. Tente novamente."
                    503 -> "Um serviço da Koi está indisponível no momento. Tente novamente mais tarde."
                    else -> "O servidor não conseguiu responder. Tente novamente."
                }
                throw IOException(explanation)
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readBoundedText(100000) }
            val value=JSONObject(body)
            val reply = value.getString("reply")
            if (reply.isBlank()) throw IOException("O servidor enviou uma resposta vazia.")
            val draft=value.optJSONObject("task_draft")
            if(draft!=null && taskDraftFromJson(draft.toString())==null) throw IOException("A proposta de tarefa está incompleta. Tente novamente.")
            val receipt=value.optJSONObject("action_receipt")
            if(receipt!=null) java.util.UUID.fromString(receipt.getString("request_id"))
            return ChatResult(reply,draft?.toString(),receipt?.toString())
        } finally {
            connection.disconnect()
        }
    }
    suspend fun undo(requestId:String,personal:Boolean=false):String {
        java.util.UUID.fromString(requestId)
        val endpoint=BackendEndpoint.resolve(BuildConfig.BACKEND_URL,BuildConfig.DEBUG)
        if(!endpoint.authenticated) throw IOException("Desfazer precisa do servidor HTTPS.")
        val token=tokenProvider() ?: throw IOException("Entre na sua conta.")
        val path=if(personal)"personal-actions" else "actions"
        val url=URL(BuildConfig.BACKEND_URL.trim().trimEnd('/')+"/api/v1/chat/$path/$requestId/undo")
        val connection=url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.requestMethod="POST"
            connection.connectTimeout=10000;connection.readTimeout=90000
            connection.setRequestProperty("Authorization","Bearer $token")
            if(connection.responseCode!=200) throw IOException(if(connection.responseCode==409) "A tarefa mudou depois desta ação. Não vou sobrescrever suas mudanças." else "Não consegui confirmar o desfazer. Tente novamente.")
            return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use {it.readBoundedText(100000)}).getString("reply")
        } finally {connection.disconnect()}
    }
}

data class ChatResult(val reply:String,val taskDraftJson:String?=null,val actionReceiptJson:String?=null)
fun taskDraftFromJson(raw:String?): com.thiago.assistentepessoal.routine.TaskDraft? = runCatching {
    val value=JSONObject(raw ?: return null)
    com.thiago.assistentepessoal.routine.TaskDraft(value.getString("title"),value.getString("notes"),
        if(value.isNull("due_date")) "" else value.getString("due_date"),
        if(value.isNull("due_time")) "" else value.getString("due_time"),value.getString("recurrence"))
        .takeIf {it.error()==null}
}.getOrNull()
