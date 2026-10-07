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
