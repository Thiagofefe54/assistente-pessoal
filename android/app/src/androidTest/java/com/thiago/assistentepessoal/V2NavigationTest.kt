package com.thiago.assistentepessoal

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.io.File

/** No chat sends, generation, bank reads or data mutations: navigation and UI state only. */
class V2NavigationTest {
    val compose=createAndroidComposeRule<MainActivity>()
    private var previousMotion=false
    private fun disableMotionForDeterministicNavigation(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("koiwai-preferences",0)
        previousMotion=prefs.getBoolean("reduce-motion",false)
        prefs.edit().putBoolean("reduce-motion",true).commit()
    }
    private fun restoreMotion(){InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("koiwai-preferences",0).edit().putBoolean("reduce-motion",previousMotion).commit()}
    @get:Rule val rules:RuleChain=RuleChain.outerRule(Timeout.seconds(60)).around(object:ExternalResource(){
        override fun before(){disableMotionForDeterministicNavigation()}
        override fun after(){restoreMotion()}
    }).around(compose)
    private fun back()=compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()}
    private fun clickRow(text:String){
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }
    private fun capture(name:String){
        compose.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.getExternalFilesDir(null),"v2-visual").apply{mkdirs()}
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun mainScreensGroupsAndDraftRemainAccessible(){
        compose.runOnUiThread{compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        capture("01-home")
        compose.onNodeWithText("Rotina").performClick()
        capture("02-routine")
        for((group,areas) in listOf(
            "Dia a dia" to listOf("Agenda","Meu ritmo","Hábitos","Listas"),
            "Pessoal" to listOf("Notas","Diário","Metas","Treinos"),
            "Dinheiro" to listOf("Finanças","Contas","Orçamento"),
            "Conexões" to listOf("Ferramentas"))){
            for(area in areas){
                compose.onNodeWithText(group).performClick()
                clickRow(area)
                back()
            }
        }
        compose.onNodeWithText("Memória").performClick()
        capture("03-memory")
        compose.onNodeWithText("Config.").performClick()
        capture("04-settings")
        for(section in listOf("Aparência e personalidade","Voz e assistente","Lembretes e carinho","Conversa e memória","Conexões e privacidade","Consumo e testes","Sobre a Koi")){
            clickRow(section)
            compose.onNodeWithText("← Todas as configurações").assertIsDisplayed()
            back()
        }
        compose.onNodeWithText("Chat").performClick()
        capture("05-chat")
        val field=compose.onNode(hasSetTextAction())
        val original=field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        try {
            field.performTextReplacement("Rascunho visual — não enviar")
            compose.onNodeWithContentDescription("Mais opções da conversa").performClick()
            compose.onNodeWithText("Escolher imagem").assertIsDisplayed()
            compose.onNodeWithText("Ferramentas").performClick()
            compose.onNodeWithText("Memória").performClick()
            compose.onNodeWithText("Chat").performClick()
            compose.onNode(hasSetTextAction()).assertTextContains("Rascunho visual — não enviar")
        } finally {compose.onNode(hasSetTextAction()).performTextReplacement(original)}
    }

    @Test fun assistantPanelIsSeparateAndCanOpenFullConversation(){
        compose.onNodeWithText("Config.").performClick()
        clickRow("Voz e assistente")
        clickRow("Experimentar painel da Koi")
        compose.onNodeWithText("Estou aqui 💜").assertIsDisplayed()
        compose.onNodeWithText("Abrir conversa completa ↗").performClick()
        compose.onNodeWithText("Digite uma mensagem…").assertExists()
    }
}
