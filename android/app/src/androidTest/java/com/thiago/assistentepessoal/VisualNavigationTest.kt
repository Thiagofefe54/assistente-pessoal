package com.thiago.assistentepessoal

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class VisualNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String) {
        compose.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.getExternalFilesDir(null),"visual-check").apply{mkdirs()}
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun back()=compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()}

    @Test fun screensRemainNavigableAndDraftSurvivesTabChanges() {
        compose.runOnUiThread {compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        capture("01-home")
        compose.onNodeWithText("Memória",substring=false).performClick()
        compose.onNodeWithText("Suas conversas, organizadas por dia.").assertIsDisplayed()
        capture("02-memory")
        compose.onNode(hasSetTextAction()).performTextReplacement("koiwai___nenhum_resultado___")
        compose.onNodeWithText("Nenhum capítulo encontrado").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Rotina",substring=false).performClick()
        compose.onNodeWithText("Tarefas",substring=false).assertIsDisplayed()
        capture("03-routine")
        compose.onNodeWithText("Agenda",substring=false).performClick()
        compose.onNodeWithText("Em preparação",substring=false).assertIsDisplayed()
        capture("04-category")
        back()
        compose.onNodeWithText("Tarefas",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Config.",substring=false).performClick()
        compose.onNodeWithText("Reduzir movimento").assertIsDisplayed()
        capture("05-settings")
        compose.onNode(hasText("Conta e sincronização") or hasText("Entrar / criar conta")).performClick()
        compose.onNodeWithText("Conta Koiwai").assertIsDisplayed()
        capture("06-account")
        back()
        compose.onNodeWithText("Reduzir movimento").assertIsDisplayed()
        compose.onNodeWithText("Chat",substring=false).performClick()
        compose.onNodeWithText("Digite uma mensagem…").assertIsDisplayed()
        capture("07-chat")
        val original=compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        try {
            compose.onNode(hasSetTextAction()).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("Rascunho de teste — não enviar")
            compose.onNodeWithText("Enviar",substring=false).assertIsDisplayed()
            capture("08-keyboard")
            compose.runOnUiThread {
                WindowInsetsControllerCompat(compose.activity.window,compose.activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
                compose.activity.currentFocus?.clearFocus()
            }
            compose.onNodeWithText("Memória",substring=false).performClick()
            compose.onNodeWithText("Chat",substring=false).performClick()
            compose.onNode(hasSetTextAction()).assertTextContains("Rascunho de teste — não enviar")
        } finally {compose.onNode(hasSetTextAction()).performTextReplacement(original)}
        back()
        compose.onNodeWithText("Sua assistente pessoal").assertIsDisplayed()
    }

    @Test fun reducedMotionPreferencePersists() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("koiwai-preferences",0)
        val original=prefs.getBoolean("reduce-motion",false)
        try {
            compose.onNodeWithText("Config.",substring=false).performClick()
            compose.onNode(isToggleable()).performClick()
            compose.waitForIdle()
            assertEquals(!original,prefs.getBoolean("reduce-motion",false))
            compose.onNodeWithText("Home",substring=false).performClick()
            compose.onNodeWithText("Config.",substring=false).performClick()
            if(original) compose.onNode(isToggleable()).assertIsOff() else compose.onNode(isToggleable()).assertIsOn()
        } finally {compose.runOnUiThread{prefs.edit().putBoolean("reduce-motion",original).commit()}}
    }
}
