package com.thiago.assistentepessoal.chat

import java.io.IOException
import java.net.URL

data class BackendEndpoint(val url: URL, val authenticated: Boolean) {
    companion object {
        fun resolve(baseUrl: String, debug: Boolean): BackendEndpoint {
            if (baseUrl.isBlank()) throw IOException("O servidor da Koiwai ainda não foi configurado.")
            val base = try { URL(baseUrl.trim().trimEnd('/')) }
                catch (error: Exception) { throw IOException("Endereço do servidor inválido.", error) }
            if (base.userInfo != null || base.query != null || base.ref != null ||
                base.path.isNotEmpty() || base.host.isBlank()) {
                throw IOException("Use somente a origem do servidor, sem credenciais ou caminho.")
            }
            val authenticated = base.protocol == "https"
            if (!authenticated && (base.protocol != "http" || !debug)) {
                throw IOException("O servidor da Koiwai precisa usar HTTPS.")
            }
            val path = if (authenticated) "/api/v1/chat" else "/api/v1/chat/demo"
            return BackendEndpoint(URL(base.toString() + path), authenticated)
        }
    }
}
