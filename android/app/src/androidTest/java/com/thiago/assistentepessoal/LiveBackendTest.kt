package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.chat.ChatBackend
import com.thiago.assistentepessoal.chat.ChatContextEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in only: uses the phone's session and sends fictional context, never its diary. */
class LiveBackendTest {
    @Test fun retryExistingGreetingThroughRepository() = runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiRetryGreeting") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val repository = app.chatRepository
        kotlinx.coroutines.withTimeout(10000) {
            while (repository.busy.value || repository.messages.value == null) kotlinx.coroutines.delay(100)
        }
        val greeting = requireNotNull(repository.messages.value).last {
            it.role == "user" && it.status == com.thiago.assistentepessoal.chat.MessageStatus.FAILED
        }
        require(greeting.content.lowercase().trim() in setOf("oi", "oii", "olá", "ola"))
        kotlinx.coroutines.withContext(Dispatchers.Main) { repository.retry(greeting) }
        kotlinx.coroutines.withTimeout(95000) { while(repository.busy.value) kotlinx.coroutines.delay(100) }
        kotlinx.coroutines.withTimeout(5000) {
            while(repository.messages.value?.find { it.id == greeting.id }?.status == com.thiago.assistentepessoal.chat.MessageStatus.SENDING) kotlinx.coroutines.delay(100)
        }
        assertTrue("O reenvio real precisa ficar salvo como enviado.",
            repository.messages.value?.find { it.id == greeting.id }?.status == com.thiago.assistentepessoal.chat.MessageStatus.SENT)
    }
    @Test fun directConversationUsesTheInstalledChatPath() = runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val account = requireNotNull(app.auth.account.value)
        val token = requireNotNull(app.auth.token(account.id))
        val connection = java.net.URL(BuildConfig.BACKEND_URL.trimEnd('/') + "/api/v1/chat").openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 90000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            val payload = org.json.JSONObject().put("message", "Oi! Diga apenas uma saudação. Não execute nenhuma ação.")
                .put("task_mode", "direct").put("request_id", java.util.UUID.randomUUID().toString())
                .put("timezone", "America/Sao_Paulo")
            if (InstrumentationRegistry.getArguments().getString("koiActualGreeting") == "true") {
                val dao = com.thiago.assistentepessoal.chat.ChatDatabase.open(app, account.id).messages()
                val failed = dao.getMessages().last { it.status == com.thiago.assistentepessoal.chat.MessageStatus.FAILED && it.role == "user" }
                require(failed.content.lowercase().trim() in setOf("oi", "oii", "olá", "ola")) { "Somente saudação permite este diagnóstico." }
                val history = com.thiago.assistentepessoal.chat.recentChatContext(dao.recentContext(failed.id, failed.occurredAt), failed)
                payload.put("message", failed.content).put("request_id", failed.id).put("timezone", failed.timezone)
                payload.put("history", org.json.JSONArray().apply { history.forEach { put(org.json.JSONObject().put("role",it.role).put("content",it.content)) } })
            }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val status = connection.responseCode
            // Only predefined server diagnostics are reported; no tokens or user data leave the phone.
            val detail = if (status != 200) runCatching {
                org.json.JSONObject(connection.errorStream.bufferedReader().use { it.readText().take(2000) }).optString("detail")
            }.getOrDefault("") else ""
            val safeDetail = detail.takeIf { it.startsWith("Não consegui") || it.startsWith("A IA") || it.startsWith("Serviço de autenticação") } ?: ""
            assertTrue("Direct chat HTTP $status: $safeDetail", status == 200)
            val response = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            assertTrue(response.getString("reply").isNotBlank())
            assertTrue(response.isNull("action_receipt"))
        } finally { connection.disconnect() }
    }
    @Test fun authenticatedConversationRecallsFictionalContext() = runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend") == "true")
        assertTrue("Configure o backend HTTPS antes do teste real.", BuildConfig.BACKEND_URL.startsWith("https://"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val account = requireNotNull(app.auth.account.value) { "Entre na Koiwai antes do teste real." }
        val backend = ChatBackend { app.auth.token(account.id) }
        val result = backend.send(
            "Qual é o nome do projeto imaginário que mencionei? Responda em uma frase curta.",
            listOf(ChatContextEntry("user", "Teste fictício: meu projeto imaginário se chama Tucano Violeta."),
                ChatContextEntry("assistant", "Entendi, seu projeto imaginário se chama Tucano Violeta.")))
        assertTrue("A resposta deve recuperar o contexto fictício.", result.contains("Tucano Violeta", ignoreCase = true))
    }
}
