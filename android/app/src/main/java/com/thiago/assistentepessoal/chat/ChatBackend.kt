package com.thiago.assistentepessoal.chat

import com.thiago.assistentepessoal.BuildConfig
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ChatBackend(private val tokenProvider: suspend () -> String? = { null }) {
    suspend fun send(message: String, history: List<ChatContextEntry> = emptyList()): String {
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
            val reply = JSONObject(body).getString("reply")
            if (reply.isBlank()) throw IOException("O servidor enviou uma resposta vazia.")
            return reply
        } finally {
            connection.disconnect()
        }
    }
}
