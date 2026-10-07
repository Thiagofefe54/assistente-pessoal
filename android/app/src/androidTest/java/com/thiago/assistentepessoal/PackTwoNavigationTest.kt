package com.thiago.assistentepessoal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PackTwoNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun reportsAndArchivedTasksAreReachableWithoutChangingData() {
        compose.onNodeWithText("Memória",substring=false).performClick()
        compose.onNodeWithText("Relatórios da Koi →").performScrollTo().performClick()
        compose.onNodeWithText("Pequenos dias.\nUma grande história.").assertIsDisplayed()
        for(label in listOf("Semana","Mês","Semestre","Ano")) {
            compose.onNodeWithText(label,substring=false).performScrollTo().performClick()
        }
        compose.onNodeWithText("Escolher data").assertExists()
        compose.onNodeWithText("← Memória").performScrollTo().performClick()
        compose.onNodeWithText("Rotina",substring=false).performClick()
        compose.onNodeWithText("Tarefas",substring=false).performClick()
        compose.onNodeWithText("Arquivadas",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Um passo.\nUma conquista.").assertExists()
    }
}
