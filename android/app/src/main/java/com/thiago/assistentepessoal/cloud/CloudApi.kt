package com.thiago.assistentepessoal.cloud

import com.thiago.assistentepessoal.BuildConfig

import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

object CloudApi {
    val URL_BASE = BuildConfig.SUPABASE_URL.trim().trimEnd('/' )
    // This publishable key is public. It grants no access to another user's data.
    val PUBLIC_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun request(path: String, method: String = "GET", body: String? = null,
                token: String? = null, prefer: String? = null): String {
        if (URL_BASE.isBlank() || PUBLIC_KEY.isBlank()) throw java.io.IOException("Configure o projeto Supabase desta instalação.")
        val origin = URL(URL_BASE)
        if (origin.protocol != "https" || origin.userInfo != null || origin.query != null ||
            origin.ref != null || origin.path.isNotEmpty()) throw IOException("Use uma origem Supabase HTTPS válida.")
        val connection = URL(URL_BASE + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = 10000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", PUBLIC_KEY)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw CloudException(code)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { connection.disconnect() }
    }
}

class CloudException(val code: Int) : IOException(when (code) {
    400, 401, 403, 422 -> "Confira seus dados e a confirmação do e-mail. Se já entrou, entre novamente."
    429 -> "Muitas tentativas. Aguarde alguns minutos."
    else -> "A nuvem está indisponível. Seu histórico continua salvo no celular."
})
