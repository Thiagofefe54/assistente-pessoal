package com.thiago.assistentepessoal.tools

import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.*

@Composable
fun ToolsScreen(onBack:()->Unit){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var section by rememberSaveable{mutableStateOf("Ferramentas")}
    var expression by rememberSaveable{mutableStateOf("")}
    var result by rememberSaveable{mutableStateOf("")}
    var query by rememberSaveable{mutableStateOf("")}
    var title by rememberSaveable{mutableStateOf("")}
    var text by rememberSaveable{mutableStateOf("")}
    var info by remember{mutableStateOf<String?>(null)}
    val prefs=context.getSharedPreferences("koiwai-focus",0)
    var end by remember{mutableLongStateOf(prefs.getLong("end",0))}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(end){while(end>0){now=System.currentTimeMillis();if(now>=end){end=0;prefs.edit().remove("end").apply();info="Sessão de foco concluída 💜 Faça uma pausa.";break};delay(1000)}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
        try{text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)!!.use{stream->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(1024)
            while(true){val size=stream.read(buffer);if(size<0)break;require(output.size()+size<=16000){"Escolha um texto de até 16 KB."};output.write(buffer,0,size)}
            String(output.toByteArray(),Charsets.UTF_8).also{require(!it.contains('\u0000')){"Escolha um arquivo de texto."}}
        }};info="Texto aberto neste celular. Você pode copiar, compartilhar ou levar à conversa."}
        catch(e:Exception){if(e is CancellationException)throw e;info=e.message ?: "Não consegui abrir o texto."}
    }}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)scope.launch{
        try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)!!.use{it.write(text.toByteArray(Charsets.UTF_8))}};info="Texto exportado."}
        catch(e:Exception){if(e is CancellationException)throw e;info="Não consegui exportar o texto."}
    }}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{TextButton(onClick=onBack){Text("← Rotina")};KoiPageHeading("SEU KIT DA KOI","Ferramentas","Abra apenas o que precisar agora.",KoiColors.Blue)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("Ferramentas","Celular","Conexões").forEach{label ->
                FilterChip(selected=section==label,onClick={section=label},label={Text(label)})
            }
        }}
        if(section=="Celular") {
            item{ContactsMessagesPanel()}
            item{DeviceAccessPanel()}
        }
        if(section=="Conexões") item{ConnectionsPanel()}
        if(section=="Ferramentas") {
        item{KoiDisclosure("Clima","Previsão para organizar o dia","spark",KoiColors.Blue){WeatherPanel()}}
        item{KoiDisclosure("Calculadora","Contas rápidas, mesmo sem internet","finance",KoiColors.Blue){KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
            Text("Calculadora",fontSize=22.sp)
            OutlinedTextField(expression,{if(it.length<=160)expression=it},label={Text("Ex.: (12,50 + 7,50) × 2")},modifier=Modifier.fillMaxWidth())
            KoiAction("Calcular",{result=runCatching{calculate(expression)}.getOrElse{it.message ?: "Confira a expressão."}},enabled=expression.isNotBlank())
            if(result.isNotBlank())Text(result,color=KoiColors.Blue,fontSize=24.sp)
            Text("% divide por 100. Também funciona sem internet.",fontSize=12.sp,color=KoiColors.Muted)
        }}}

        item{KoiDisclosure("Pesquisar na internet","Encontre e confira as fontes","spark",KoiColors.Blue){KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
            Text("Pesquisar na internet",fontSize=22.sp)
            OutlinedTextField(query,{if(it.length<=300)query=it},label={Text("O que você quer encontrar?")},modifier=Modifier.fillMaxWidth())
            KoiAction("Abrir pesquisa",{searchWeb(context,query)},enabled=query.isNotBlank())
            Text("Abre o navegador para você conferir as fontes.",fontSize=12.sp,color=KoiColors.Muted)
        }}}

        item{KoiDisclosure("Foco e pausa","Um tempo para você se concentrar","tasks",KoiColors.Blue){KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Red){
            Text("Foco e pausa",fontSize=22.sp)
            Text(if(end>0){val seconds=((end-now)/1000).coerceAtLeast(0);"${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"}else "Escolha seu ritmo.",fontSize=30.sp)
            Row{TextButton(onClick={end=System.currentTimeMillis()+25*60000;prefs.edit().putLong("end",end).apply()}){Text("Foco 25 min")};TextButton(onClick={end=System.currentTimeMillis()+5*60000;prefs.edit().putLong("end",end).apply()}){Text("Pausa 5 min")}}
            if(end>0)TextButton(onClick={end=0;prefs.edit().remove("end").apply()}){Text("Encerrar")}
            Text("O horário fica salvo ao sair. Confira o contador aqui; este timer não envia avisos.",fontSize=12.sp,color=KoiColors.Muted)
        }}}

        item{KoiDisclosure("Texto e documentos","Abrir, escrever e compartilhar","notes",KoiColors.Blue){KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple){
            Text("Texto e documentos",fontSize=22.sp)
            Row{TextButton(onClick={import.launch(arrayOf("text/plain"))}){Text("Abrir TXT")};TextButton(onClick={export.launch("Koiwai.txt")},enabled=text.isNotBlank()){Text("Exportar TXT")}}
            OutlinedTextField(text,{if(it.length<=16000)text=it},label={Text("Seu texto")},minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={shareText(context,"Texto da Koiwai",text)},enabled=text.isNotBlank()){Text("Compartilhar texto")}
            Text("Arquivos TXT em UTF-8, até 16 KB.",fontSize=12.sp,color=KoiColors.Muted)
        }}}

        item{KoiDisclosure("Calendário do celular","Prepare um novo compromisso","agenda",KoiColors.Blue){KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
            Text("Calendário do celular",fontSize=22.sp)
            OutlinedTextField(title,{if(it.length<=160)title=it},label={Text("Título do compromisso")},modifier=Modifier.fillMaxWidth())
            KoiAction("Abrir no calendário",{openIntent(context,Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE,title))},enabled=title.isNotBlank())
            Text("Escolha a data e salve no aplicativo de calendário. A Koi só abre o formulário.",fontSize=12.sp,color=KoiColors.Muted)
        }}}

        }
        info?.let{item{Text(it,color=KoiColors.Blue)}}
    }
}
