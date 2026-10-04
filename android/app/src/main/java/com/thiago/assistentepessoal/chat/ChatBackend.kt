package com.thiago.assistentepessoal.chat

import com.thiago.assistentepessoal.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ChatBackend(private val tokenProvider: suspend () -> String? = { null }) {
    suspend fun send(message: String): String {
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
            connection.readTimeout = 15000
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(JSONObject().put("message", message).toString())
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("O servidor não conseguiu responder. Tente novamente.")
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
