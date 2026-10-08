package com.thiago.assistentepessoal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MobileV1SettingsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun newSettingsButtonsAreAccessibleWithoutChangingPreferences(){
        compose.onNodeWithText("Config.",substring=false).performClick()
        for(label in listOf("Ouvir novas respostas","Testar voz","Configurar voz","Parar voz","Ferramentas e celular","Gerenciar lembranças","Permissões de notificações")){
            compose.onNodeWithText(label,substring=false).performScrollTo().assertIsDisplayed()
        }
    }
}
