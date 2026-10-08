package com.thiago.assistentepessoal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Read-only checks for the next connected Poco session. No personal records written. */
class MobileV1NavigationTest{
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun personalAreasAndToolsHaveRealScreens(){
        for(area in listOf("Notas","Listas","Metas","Treinos","Finanças","Agenda","Hábitos")){
            compose.onNodeWithText("Rotina",substring=false).performClick()
            compose.onNodeWithText(area,substring=false).performScrollTo().performClick()
            compose.onNodeWithText("← Rotina",substring=false).assertExists().performClick()
        }
        compose.onNodeWithText("Ferramentas",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Calculadora",substring=false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pesquisar na internet",substring=false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Foco e pausa",substring=false).performScrollTo().assertIsDisplayed()
    }
}
