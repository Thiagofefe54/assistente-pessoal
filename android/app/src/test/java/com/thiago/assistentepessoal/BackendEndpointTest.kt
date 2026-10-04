package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.chat.BackendEndpoint
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BackendEndpointTest {
    @Test fun httpsUsesAuthenticatedChat() {
        val endpoint = BackendEndpoint.resolve("https://koi.example/", false)
        assertTrue(endpoint.authenticated)
        assertEquals("https://koi.example/api/v1/chat", endpoint.url.toString())
    }
    @Test fun debugHttpUsesDemoWithoutAuthentication() {
        val endpoint = BackendEndpoint.resolve("http://127.0.0.1:8000", true)
        assertFalse(endpoint.authenticated)
        assertEquals("/api/v1/chat/demo", endpoint.url.path)
    }
    @Test fun unsafeConfigurationIsRejected() {
        listOf("http://127.0.0.1:8000", "", "ftp://koi.example", "https://u:p@koi.example",
            "https://koi.example/path", "https://koi.example?token=x", "https://koi.example#x")
            .forEach { url ->
                try { BackendEndpoint.resolve(url, false); fail("Aceitou: $url") }
                catch (_: IOException) { }
            }
    }
}
