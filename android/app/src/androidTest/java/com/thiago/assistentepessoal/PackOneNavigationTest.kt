package com.thiago.assistentepessoal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PackOneNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun missionEditorRequiresTitleAndUndatedRepeatCannotSave() {
        compose.onNodeWithText("Rotina",substring=false).performClick()
        compose.onNodeWithText("Tarefas",substring=false).performClick()
        compose.onNodeWithText("Um passo.\nUma conquista.").assertIsDisplayed()
        compose.waitUntil(25000) {compose.onAllNodesWithText("＋ Nova tarefa").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        }}
        compose.onNodeWithText("＋ Nova tarefa").performClick()
        compose.onNodeWithText("Salvar tarefa").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Rascunho fictício — cancelar")
        compose.onNodeWithText("Salvar tarefa").assertIsEnabled()
        compose.onNodeWithText("Diária",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Salvar tarefa").assertIsNotEnabled()
        compose.onNode(hasText("Uma vez",substring=false) and hasClickAction()).performClick()
        compose.onNodeWithText("Salvar tarefa").assertIsEnabled()
        compose.onNodeWithText("Cancelar",substring=false).performClick()
        compose.onNodeWithText("Nova missão").assertDoesNotExist()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText("Seu próximo\npasso.").assertIsDisplayed()
    }
}
