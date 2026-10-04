package com.thiago.assistentepessoal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun tabsAndNestedPagesReturnToTheirOrigin() {
        compose.onNodeWithText("Memória").performClick()
        compose.onNodeWithText("Suas conversas, organizadas por dia.").assertIsDisplayed()
        compose.onNodeWithText("Rotina").performClick()
        compose.onNodeWithText("Tarefas").performClick()
        compose.onNodeWithText("Em preparação", substring = false).assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Agenda").assertIsDisplayed()
        compose.onNodeWithText("Config.").performClick()
        compose.onNodeWithText("Reduzir movimento").assertIsDisplayed()
        compose.onNode(hasText("Conta e sincronização") or hasText("Entrar / criar conta")).performClick()
        compose.onNodeWithText("Conta Koiwai").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Reduzir movimento").assertIsDisplayed()
        compose.onNodeWithText("Chat", substring = false).performClick()
        compose.onNodeWithText("Digite uma mensagem…").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Sua assistente pessoal").assertIsDisplayed()
    }
}
