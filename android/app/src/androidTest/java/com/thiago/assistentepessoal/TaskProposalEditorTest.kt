package com.thiago.assistentepessoal

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import com.thiago.assistentepessoal.routine.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TaskProposalEditorTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun draftIsEditableBeforeConfirmationAndSavingDisablesSecondSubmission() {
        var result:TaskDraft?=null
        var saving by mutableStateOf(false)
        compose.runOnUiThread {compose.activity.setContent {KoiwaiTheme {
            TaskEditor(existing=null,onDismiss={},initial=TaskDraft("Missão fictícia","","2026-10-08","09:15","none"),saving=saving) {result=it;saving=true}
        }}}
        compose.onAllNodes(hasSetTextAction())[0].assertTextContains("Missão fictícia")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Missão fictícia revisada")
        compose.onNodeWithText("Salvar tarefa").performClick()
        compose.onNodeWithText("Salvando…").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals("Missão fictícia revisada",result?.title)
            assertEquals("2026-10-08",result?.date);assertEquals("09:15",result?.time)
        }
    }
}
