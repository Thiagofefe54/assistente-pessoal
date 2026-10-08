package com.thiago.assistentepessoal

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private val taskRequest = mutableIntStateOf(0)
    private val reportRequest=mutableStateOf<String?>(null)
    private var reportCounter=0
    private val lifeRequest=mutableStateOf<String?>(null)
    private fun lifeIntent(intent:Intent){intent.getStringExtra("openLife")?.takeIf{it in listOf("Contas","Orçamento","Diário")}?.let{lifeRequest.value="$it|${System.nanoTime()}"}}
    private fun reportIntent(intent:Intent) {
        val kind=intent.getStringExtra("reportKind") ?: return
        val anchor=intent.getStringExtra("reportAnchor") ?: return
        if(kind !in com.thiago.assistentepessoal.memory.reportKinds && kind!="day") return
        if(runCatching {java.time.LocalDate.parse(anchor)}.isFailure) return
        reportRequest.value="$kind|$anchor|${++reportCounter}"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent.getBooleanExtra("openTasks",false)) taskRequest.intValue++
        reportIntent(intent)
        lifeIntent(intent)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { KoiwaiTheme { MotionEnvironment { KoiwaiNavigation(taskRequest.intValue,reportRequest.value,lifeRequest.value) } } }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if(intent.getBooleanExtra("openTasks",false)) taskRequest.intValue++
        reportIntent(intent)
        lifeIntent(intent)
    }
}
