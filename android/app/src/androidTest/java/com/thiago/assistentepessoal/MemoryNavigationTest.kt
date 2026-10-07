package com.thiago.assistentepessoal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MemoryNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun confirmedMemoriesEditorCanBeCancelledAndBackReturnsToDiary() {
        compose.onNodeWithText("Memória",substring=false).performClick()
        compose.onNodeWithText("Lembranças confirmadas →").performClick()
        compose.onNodeWithText("LEMBRANÇAS CONFIRMADAS").assertIsDisplayed()
        compose.waitUntil(25000) {compose.onAllNodesWithText("＋ Nova lembrança").fetchSemanticsNodes().any {
            it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()
        }}
        compose.onNodeWithText("＋ Nova lembrança").performClick()
        compose.onNodeWithText("Confirmar e salvar").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Rascunho fictício — cancelar")
        compose.onNodeWithText("Confirmar e salvar").assertIsEnabled()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Confirmar lembrança").assertDoesNotExist()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText("Suas conversas, organizadas por dia.").assertIsDisplayed()
    }
}
