package com.thiago.assistentepessoal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AssistantApp()
            }
        }
    }
}

@Composable
fun AssistantApp() {

    var currentScreen by remember {
        mutableStateOf("home")
    }

    when (currentScreen) {

        "home" -> AssistantHome(
            onOpenChat = {
                currentScreen = "chat"
            }
        )

        "chat" -> ChatScreen(
            onBack = {
                currentScreen = "home"
            }
        )
    }
}

@Composable
fun AssistantHome(
    onOpenChat: () -> Unit
) {

    val backgroundTop = Color(0xFF0B0714)
    val backgroundBottom = Color(0xFF151027)

    val purple = Color(0xFF9B5CFF)
    val blue = Color(0xFF547CFF)

    val cardColor = Color(0xFF1C1630)
    val secondaryText = Color(0xFFAAA2BD)

    Scaffold(
        containerColor = Color.Transparent
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            backgroundTop,
                            backgroundBottom
                        )
                    )
                )
                .padding(paddingValues)
                .padding(
                    horizontal = 20.dp,
                    vertical = 18.dp
                )
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column {

                    Text(
                        text = "Boa noite, Mestre.",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "21:40 • 03 de outubro",
                        color = secondaryText,
                        fontSize = 14.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                color = Color(0xFF56E39F),
                                shape = CircleShape
                            )
                    )

                    Text(
                        text = " Online",
                        color = secondaryText,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Image(
                painter = painterResource(
                    id = R.drawable.koiwai
                ),
                contentDescription = "Koiwai",
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "Koiwai",
                modifier = Modifier.align(
                    Alignment.CenterHorizontally
                ),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Sua assistente pessoal",
                modifier = Modifier.align(
                    Alignment.CenterHorizontally
                ),
                color = secondaryText,
                fontSize = 14.sp
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    12.dp
                )
            ) {

                DashboardCard(
                    title = "Clima",
                    value = "-- °C",
                    subtitle = "Ainda não configurado",
                    color = purple,
                    modifier = Modifier.weight(1f)
                )

                DashboardCard(
                    title = "Status",
                    value = "Online",
                    subtitle = "Poco conectado",
                    color = blue,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text = "Próximas tarefas",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = cardColor
                ),
                shape = RoundedCornerShape(18.dp)
            ) {

                Column(
                    modifier = Modifier.padding(18.dp)
                ) {

                    Text(
                        text = "Nenhuma tarefa ainda",
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Quando adicionarmos sua agenda, ela aparecerá aqui.",
                        color = secondaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(
                            top = 4.dp
                        )
                    )
                }
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onOpenChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = purple
                ),
                shape = RoundedCornerShape(18.dp)
            ) {

                Text(
                    text = "✦  Conversar comigo",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {

                BottomItem(
                    "⌂",
                    "Home",
                    purple
                )

                BottomItem(
                    "◉",
                    "Chat",
                    secondaryText
                )

                BottomItem(
                    "◆",
                    "Memória",
                    secondaryText
                )

                BottomItem(
                    "✓",
                    "Rotina",
                    secondaryText
                )

                BottomItem(
                    "⚙",
                    "Config.",
                    secondaryText
                )
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1630)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = title,
                color = color,
                fontSize = 13.sp
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = value,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                color = Color(0xFFAAA2BD),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun BottomItem(
    icon: String,
    label: String,
    color: Color
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = icon,
            color = color,
            fontSize = 20.sp
        )

        Text(
            text = label,
            color = color,
            fontSize = 10.sp
        )
    }
}

@Composable
fun ChatScreen(
    onBack: () -> Unit
) {

    val purple = Color(0xFF9B5CFF)
    val backgroundTop = Color(0xFF0B0714)
    val backgroundBottom = Color(0xFF151027)

    val cardColor = Color(0xFF1C1630)
    val secondaryText = Color(0xFFAAA2BD)

    var input by remember {
        mutableStateOf("")
    }

    var userMessage by remember {
        mutableStateOf("")
    }

    var assistantMessage by remember {
        mutableStateOf(
            "Olá, Mestre. Como posso ajudar?"
        )
    }

    var loading by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundTop,
                        backgroundBottom
                    )
                )
            )
            .padding(20.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cardColor
                )
            ) {
                Text("←")
            }

            Column(
                modifier = Modifier.padding(
                    start = 14.dp
                )
            ) {

                Text(
                    text = "Koiwai",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "● Online",
                    color = Color(0xFF56E39F),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        if (assistantMessage.isNotBlank()) {

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = cardColor
                ),
                shape = RoundedCornerShape(18.dp)
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "Koiwai",
                        color = purple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = assistantMessage,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }

        if (userMessage.isNotBlank()) {

            Card(
                modifier = Modifier.align(
                    Alignment.End
                ),
                colors = CardDefaults.cardColors(
                    containerColor = purple
                ),
                shape = RoundedCornerShape(18.dp)
            ) {

                Text(
                    text = userMessage,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.weight(1f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                },
                placeholder = {
                    Text(
                        text = "Digite uma mensagem...",
                        color = secondaryText
                    )
                },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {

                    if (
                        input.isNotBlank() &&
                        !loading
                    ) {

                        val messageToSend = input

                        userMessage = messageToSend

                        input = ""

                        loading = true

                        scope.launch {

                            val reply = withContext(
                                Dispatchers.IO
                            ) {

                                sendMessageToBackend(
                                    messageToSend
                                )
                            }

                            assistantMessage = reply

                            loading = false
                        }
                    }
                },
                modifier = Modifier.padding(
                    start = 8.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = purple
                )
            ) {

                Text(
                    text = if (loading) {
                        "..."
                    } else {
                        "➤"
                    }
                )
            }
        }
    }
}

fun sendMessageToBackend(
    message: String
): String {

    return try {

        val url = URL(
            "http://192.168.18.182:8000/api/v1/chat"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        connection.requestMethod = "POST"

        connection.setRequestProperty(
            "Content-Type",
            "application/json"
        )

        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        val escapedMessage = message
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        val body =
            """{"message":"$escapedMessage"}"""

        OutputStreamWriter(
            connection.outputStream
        ).use {
            it.write(body)
        }

        val responseCode =
            connection.responseCode

        if (responseCode != 200) {

            connection.disconnect()

            return "Mestre, ocorreu um erro ao conversar com o servidor."
        }

        val response =
            connection.inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }

        connection.disconnect()

        val reply =
            Regex(
                """"reply"\s*:\s*"([^"]*)""""
            )
                .find(response)
                ?.groupValues
                ?.get(1)

        reply
            ?: "Mestre, recebi uma resposta inválida."

    } catch (exception: Exception) {

        "Mestre, não consegui acessar o servidor."
    }
}