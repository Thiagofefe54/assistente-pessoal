package com.thiago.assistentepessoal.cloud

import com.thiago.assistentepessoal.BuildConfig

import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.io.Reader

object CloudApi {
    val URL_BASE = BuildConfig.SUPABASE_URL.trim().trimEnd('/' )
    // This publishable key is public. It grants no access to another user's data.
    val PUBLIC_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun request(path: String, method: String = "GET", body: String? = null,
                token: String? = null, prefer: String? = null): String {
        if (URL_BASE.isBlank() || PUBLIC_KEY.isBlank()) throw java.io.IOException("Configure o projeto Supabase desta instalação.")
        val connection = cloudEndpoint(URL_BASE,path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = 10000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", PUBLIC_KEY)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw CloudException(code)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readBoundedText() }
        } finally { connection.disconnect() }
    }
}

/** Validate the destination before adding a bearer token, including future callers. */
fun cloudEndpoint(base:String,path:String): URL {
    val origin=try {URL(base)} catch(_:Exception) {throw IOException("Use uma origem Supabase HTTPS válida.")}
    if(origin.protocol!="https" || origin.host.isBlank() || origin.userInfo!=null || origin.query!=null ||
        origin.ref!=null || origin.path.isNotEmpty() || !path.startsWith("/") || path.startsWith("//"))
        throw IOException("Use uma origem Supabase HTTPS e um caminho válido.")
    val target=URL(base+path)
    if(target.protocol!=origin.protocol || target.host!=origin.host || target.port!=origin.port || target.userInfo!=null)
        throw IOException("O destino da nuvem mudou. Confira a configuração.")
    return target
}

fun Reader.readBoundedText(limit:Int=8_000_000):String {
    val result=StringBuilder()
    val buffer=CharArray(8192)
    while(true) {
        val length=read(buffer)
        if(length<0) break
        if(result.length+length>limit) throw IOException("A resposta excedeu o limite. Tente sincronizar novamente.")
        result.append(buffer,0,length)
    }
    return result.toString()
}

class CloudException(val code: Int) : IOException(when (code) {
    400, 401, 403, 422 -> "Confira seus dados e a confirmação do e-mail. Se já entrou, entre novamente."
    429 -> "Muitas tentativas. Aguarde alguns minutos."
    else -> "A nuvem está indisponível. Seu histórico continua salvo no celular."
})
