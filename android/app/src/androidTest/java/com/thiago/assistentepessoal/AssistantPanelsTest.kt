package com.thiago.assistentepessoal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Reads only. No fictional records, chat generations, changes or exported session. */
class AssistantPanelsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun newPanelsAreReachableWithoutChangingPreferences() {
        compose.onNodeWithText("Conferir meu dia").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Config.", substring=false).performClick()
        compose.onNodeWithText("Consultar saldo").performScrollTo().assertIsDisplayed()
    }

    @Test fun liveDayAndPointsReadWithoutGeneratingConversation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLivePanel") == "true")
        val app = compose.activity.application as KoiwaiApplication
        assumeTrue("Entre na conta antes de testar.", app.auth.account.value != null)
        compose.onNodeWithText("Conferir meu dia").performScrollTo().performClick()
        compose.waitUntil(100000) { compose.onAllNodes(hasText("missões para hoje", substring=true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Diário e comparação ↓").performScrollTo().performClick()
        compose.onNodeWithText("Comparação de despesas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Config.", substring=false).performClick()
        compose.onNodeWithText("Consultar saldo").performScrollTo().performClick()
        compose.waitUntil(100000) { compose.onAllNodes(hasText("pontos disponíveis", substring=true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Consultar saldo").performScrollTo().assertIsDisplayed()
    }
}
