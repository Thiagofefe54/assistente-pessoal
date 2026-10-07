package com.thiago.assistentepessoal.chat

import com.thiago.assistentepessoal.BuildConfig
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ChatBackend(private val tokenProvider: suspend () -> String? = { null }) {
    suspend fun send(message: String, history: List<ChatContextEntry> = emptyList()): String = sendResult(message,history).reply
    suspend fun sendResult(message: String, history: List<ChatContextEntry> = emptyList()): ChatResult {
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
                    payload.put("timezone",java.time.ZoneId.systemDefault().id)
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
                    502, 503 -> "A IA está indisponível ou ainda não foi configurada. Tente novamente mais tarde."
                    else -> "O servidor não conseguiu responder. Tente novamente."
                }
                throw IOException(explanation)
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val value=JSONObject(body)
            val reply = value.getString("reply")
            if (reply.isBlank()) throw IOException("O servidor enviou uma resposta vazia.")
            val draft=value.optJSONObject("task_draft")
            if(draft!=null && taskDraftFromJson(draft.toString())==null) throw IOException("A proposta de tarefa está incompleta. Tente novamente.")
            return ChatResult(reply,draft?.toString())
        } finally {
            connection.disconnect()
        }
    }
}

data class ChatResult(val reply:String,val taskDraftJson:String?=null)
fun taskDraftFromJson(raw:String?): com.thiago.assistentepessoal.routine.TaskDraft? = runCatching {
    val value=JSONObject(raw ?: return null)
    com.thiago.assistentepessoal.routine.TaskDraft(value.getString("title"),value.getString("notes"),
        if(value.isNull("due_date")) "" else value.getString("due_date"),
        if(value.isNull("due_time")) "" else value.getString("due_time"),value.getString("recurrence"))
        .takeIf {it.error()==null}
}.getOrNull()
