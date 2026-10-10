package com.thiago.assistentepessoal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.chat.*
import com.thiago.assistentepessoal.cloud.AccountScreen
import com.thiago.assistentepessoal.memory.*
import com.thiago.assistentepessoal.routine.TasksScreen
import com.thiago.assistentepessoal.routine.PersonalScreen
import com.thiago.assistentepessoal.tools.ToolsScreen
import com.thiago.assistentepessoal.tools.WeatherPanel
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tabs = listOf("home" to "Home", "chat" to "Chat", "memory" to "Memória", "routine" to "Rotina", "settings" to "Config.")
private val pt = Locale.forLanguageTag("pt-BR")

@Composable
fun KoiwaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=KoiColors.Purple,onPrimary=Color.White,
        secondary=KoiColors.Blue,background=KoiColors.Ink,surface=KoiColors.Card,
        onSurface=Color.White,onBackground=Color.White,onSurfaceVariant=KoiColors.Muted,
        outline=Color(0xFF535C78)),shapes=Shapes(small=RoundedCornerShape(14.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp))) {
        CompositionLocalProvider(LocalContentColor provides Color.White,content=content)
    }
}

@Composable
fun KoiwaiNavigation(taskRequest:Int=0,reportRequest:String?=null,lifeRequest:String?=null,assistRequest:Int=0) {
    var selected by rememberSaveable { mutableStateOf("home") }
    var accountReturn by rememberSaveable { mutableStateOf("settings") }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var routineGroup by rememberSaveable {mutableStateOf("Dia a dia")}
    val states = rememberSaveableStateHolder()
    val app = LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val motion = LocalKoiMotion.current
    LaunchedEffect(account?.id) { day=null }
    LaunchedEffect(assistRequest){if(assistRequest>0){selected="chat";day=null;category=null}}
    LaunchedEffect(taskRequest) { if(taskRequest>0) {selected="routine";category="Tarefas";day=null} }
    LaunchedEffect(lifeRequest){lifeRequest?.split('|')?.firstOrNull()?.takeIf{it in listOf("Contas","Orçamento","Diário","Meu ritmo")}?.let{selected="routine";category=it;day=null}}
    LaunchedEffect(reportRequest) {reportRequest?.split('|')?.let { parts ->
        if(parts[0]=="day") {day=parts[1];selected="memory"} else {day=null;selected="reports"}
    }}
    fun back() { when {
        selected=="account" -> selected=accountReturn
        selected=="facts" -> selected="memory"
        selected=="reports" -> selected="memory"
        selected=="memory" && day!=null -> day=null
        selected=="routine" && category!=null -> category=null
        else -> selected="home"
    } }
    fun openAccount(from: String) { accountReturn=from; selected="account" }
    BackHandler(enabled=selected!="home") { back() }
    Box(Modifier.fillMaxSize()) {
        KoiBackdrop(selected)
        Scaffold(containerColor=Color.Transparent, bottomBar={
            if(selected!="account") Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=14.dp,vertical=8.dp)) {
                Surface(color=Color(0xFF181C30),shadowElevation=10.dp,shape=RoundedCornerShape(26.dp),modifier=Modifier.border(1.dp,Color.White.copy(alpha=.1f),RoundedCornerShape(26.dp))) {
                    Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        tabs.forEach { (route,label) ->
                            val active=selected==route || (route=="memory" && selected in listOf("facts","reports"))
                            val accent=when(route){"routine"->KoiColors.Red;"memory","settings"->KoiColors.Blue;else->KoiColors.Purple}
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if(active)accent.copy(alpha=.16f) else Color.Transparent)
                                .selectable(active,onClick={selected=route;day=null;category=null},role=Role.Tab).padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
                                KoiGlyph(route,if(active)accent else KoiColors.Muted,Modifier.size(24.dp))
                                Text(label,fontSize=10.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,color=if(active)Color.White else KoiColors.Muted)
                            }
                        }
                    }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                AnimatedContent(targetState=selected, label="page", transitionSpec={
                    if(motion) (fadeIn(tween(300))+slideInVertically(tween(350)){it/35}) togetherWith fadeOut(tween(140))
                    else fadeIn(snap()) togetherWith fadeOut(snap())
                }) { route ->
                    states.SaveableStateProvider("$route:${account?.id ?: "local"}") {
                        when(route) {
                            "home" -> HomeScreen({selected="chat"},{selected="routine"},{openAccount("home")},{selected="routine";category="Meu ritmo"},{selected="memory"})
                            "chat" -> ChatScreen({back()},{openAccount("chat")},{day=it;selected="memory"},{selected="routine";category="Ferramentas"},{area->
                                if(area=="Lembranças") selected="facts" else {selected="routine";category=area}
                            })
                            "memory" -> MemoryScreen(day,{day=it},{day=null},{selected="facts"},{selected="reports"},{area->selected="routine";category=area})
                            "reports" -> key(reportRequest) {ReportsScreen({selected="memory"},{day=it;selected="memory"},
                                reportRequest?.split('|')?.get(0) ?: "week",reportRequest?.split('|')?.get(1) ?: java.time.LocalDate.now().toString())}
                            "facts" -> MemoriesScreen({selected="memory"},{openAccount("facts")})
                            "routine" -> when(category) {
                                "Meu ritmo" -> CompanionScreen({category=null},{category=it})
                                "Contas","Orçamento","Diário" -> key(category){com.thiago.assistentepessoal.routine.LifeScreen(category!!,{category=null},{openAccount("routine")})}
                                "Tarefas","Agenda","Hábitos" -> key(category){TasksScreen({category=null},{openAccount("routine")},category ?: "Tarefas")}
                                "Notas","Listas","Metas","Treinos","Finanças","Registros" -> key(category){PersonalScreen(if(category=="Registros") "Notas" else category!!,{category=null},{openAccount("routine")})}
                                "Ferramentas" -> ToolsScreen({category=null})
                                else -> RoutineScreen(routineGroup,{routineGroup=it},{category=it})
                            }
                            "settings" -> SettingsScreen({openAccount("settings")},{selected="routine";category="Ferramentas"},{selected="facts"})
                            "account" -> AccountScreen {back()}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onChat:()->Unit,onRoutine:()->Unit,onAccount:()->Unit,onCompanion:()->Unit,onMemory:()->Unit) {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val repo by app.tasks.collectAsState()
    val tasks=repo?.tasks?.collectAsState()?.value
    LaunchedEffect(repo){repo?.refresh()}
    val prefs=LocalContext.current.getSharedPreferences("koiwai-preferences",0)
    val treatment=prefs.getString("treatment","Mestre") ?: "Mestre"
    var now by remember{mutableStateOf(ZonedDateTime.now())}
    LaunchedEffect(Unit){while(true){now=ZonedDateTime.now();delay(30000)}}
    val greeting=when(now.hour){in 5..11->"Bom dia";in 12..17->"Boa tarde";else->"Boa noite"}
    val pending=tasks.orEmpty().filter{it.completedAt==null && it.archivedAt==null}
    val next=pending.sortedWith(compareBy<com.thiago.assistentepessoal.routine.KoiTask>{it.date ?: "9999"}.thenBy{it.time ?: "99"}).take(2)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){
                    Eyebrow(now.format(DateTimeFormatter.ofPattern("EEEE • dd MMM",pt)).uppercase(pt),KoiColors.Blue)
                    Spacer(Modifier.height(6.dp))
                    Text("$greeting, $treatment.",fontSize=24.sp,fontWeight=FontWeight.Bold,lineHeight=30.sp)
                }
                IconButton(onClick=onAccount){KoiGlyph("settings",KoiColors.Blue)}
            }
        }
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        KoiChip("SEU ESPAÇO COM A KOI",KoiColors.Purple)
                        Text("Vamos cuidar\ndo seu dia?",fontSize=29.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
                        Text("Sua assistente pessoal",color=KoiColors.Muted,fontSize=13.sp)
                    }
                    Box(Modifier.size(116.dp),contentAlignment=Alignment.Center){
                        OrbitEmblem("spark",KoiColors.Purple,Modifier.fillMaxSize())
                        Image(painterResource(R.drawable.koiwai),"Koiwai",Modifier.size(108.dp))
                    }
                }
                KoiAction("✦  Conversar com a Koi",onChat,Modifier.fillMaxWidth())
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                KoiPanel(Modifier.weight(1f),accent=KoiColors.Red,onClick=onRoutine){
                    KoiGlyph("routine",KoiColors.Red);Text("Minha rotina",fontWeight=FontWeight.SemiBold)
                    Text("Planos e missões",color=KoiColors.Muted,fontSize=12.sp)
                }
                KoiPanel(Modifier.weight(1f),accent=KoiColors.Blue,onClick=onMemory){
                    KoiGlyph("memory",KoiColors.Blue);Text("Minha história",fontWeight=FontWeight.SemiBold)
                    Text("Diário e lembranças",color=KoiColors.Muted,fontSize=12.sp)
                }
            }
        }
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Red,onClick=onRoutine) {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text("Próximos passos",fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    KoiChip(if(tasks==null) "Rotina" else "${pending.size} pendentes",KoiColors.Red)
                }
                if(account==null)Text("Conecte sua conta para organizar suas missões.",color=KoiColors.Muted,fontSize=13.sp)
                else if(tasks==null)Text("Abra sua rotina para conferir as tarefas.",color=KoiColors.Muted,fontSize=13.sp)
                else if(next.isEmpty())Text("Um espaço livre para seu próximo plano 💜",color=KoiColors.Muted,fontSize=13.sp)
                next.forEach {task ->
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        KoiGlyph("tasks",if(task.overdue())KoiColors.Red else KoiColors.Blue,Modifier.size(18.dp))
                        Text(task.title,Modifier.weight(1f),fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                        task.time?.let{Text(it.take(5),fontSize=12.sp,color=KoiColors.Muted)}
                    }
                }
            }
        }
        item {KoiMenuRow("Meu ritmo","Hábitos, check-ins e pequenas conquistas","training",KoiColors.Purple,onCompanion)}
        item {KoiDisclosure("Explorar meu dia","Resumo, planejamento e previsão do tempo","agenda",KoiColors.Blue){
            DayOverviewPanel(onRoutine);DayPlanPanel(onRoutine);WeatherPanel(compact=true)
        }}
        item {Text("KOIWAI • UM PASSO DE CADA VEZ",fontSize=9.sp,letterSpacing=1.4.sp,color=KoiColors.Muted,modifier=Modifier.padding(vertical=4.dp))}
    }
}

@Composable
private fun MemoryScreen(day: String?, onDay: (String)->Unit, onBack: ()->Unit, onFacts: ()->Unit,onReports:()->Unit,onArea:(String)->Unit) {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val repo by app.repositories.collectAsState()
    val history by repo.messages.collectAsState()
    val error by repo.error.collectAsState()
    val messages=history.orEmpty()
    val memories by app.memories.collectAsState()
    var remembering by remember { mutableStateOf<ChatMessage?>(null) }
    var factText by remember { mutableStateOf("") }
    var factCategory by remember { mutableStateOf("note") }
    var query by rememberSaveable { mutableStateOf("") }
    val days=remember(messages,query) { messages.groupBy {it.localDate}.filter { (date,entries)-> query.isBlank() || date.contains(query) || dayLabel(date).contains(query,true) || entries.any {it.content.contains(query,true)} }.toSortedMap(compareByDescending {it}) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            KoiPageHeading("ARQUIVO PESSOAL",if(day==null) "Cada dia,\num capítulo." else dayLabel(day),"Suas conversas, organizadas por dia.",KoiColors.Blue,"memory")
        }
        if(day==null) {
            item {KoiDisclosure("Buscar com a Koi","Encontre lembranças, relatos e planos","spark",KoiColors.Blue){RecallPanel(onDay,onFacts,onArea)}}
            item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                KoiPanel(Modifier.weight(1f),KoiColors.Blue,onReports){KoiGlyph("agenda",KoiColors.Blue);Text("Relatórios",fontWeight=FontWeight.SemiBold);Text("Seus capítulos",fontSize=12.sp,color=KoiColors.Muted)}
                KoiPanel(Modifier.weight(1f),KoiColors.Purple,onFacts){KoiGlyph("memory");Text("Lembranças",fontWeight=FontWeight.SemiBold);Text("O que fica comigo",fontSize=12.sp,color=KoiColors.Muted)}
            }}
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val dayCount=messages.map{it.localDate}.distinct().size
                    KoiChip("$dayCount ${if(dayCount==1) "dia" else "dias"}",KoiColors.Blue)
                    KoiChip("${messages.size} ${if(messages.size==1) "mensagem" else "mensagens"}")
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(query,{query=it},placeholder={Text("Buscar nas conversas…")},singleLine=true,
                    shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth())
            }
            if(history==null) item { Text(error ?: "Carregando histórico…",color=KoiColors.Muted) }
            else if(days.isEmpty()) item {
                KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
                    OrbitEmblem("memory",KoiColors.Blue)
                    Text(if(query.isBlank()) "Sua história começa aqui" else "Nenhum capítulo encontrado",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                    Text(if(query.isBlank()) "Converse com a Koi. As mensagens salvas vão formar sua linha do tempo." else "Tente outra palavra ou data.",color=KoiColors.Muted)
                }
            }
            if(days.isNotEmpty())item{Eyebrow("SEUS CAPÍTULOS",KoiColors.Blue)}
            items(days.keys.toList().chunked(2),key={it.joinToString("|")}) { pair ->
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    pair.forEach{date->
                    val entries=days.getValue(date)
                    val parsed=LocalDate.parse(date)
                    KoiPanel(Modifier.weight(1f),accent=KoiColors.Blue,onClick={onDay(date)}) {
                        Eyebrow(parsed.format(DateTimeFormatter.ofPattern("MMM / yyyy",pt)).uppercase(pt),KoiColors.Blue)
                        Text(parsed.dayOfMonth.toString().padStart(2,'0'),fontSize=44.sp,fontWeight=FontWeight.Light)
                        Text(if(parsed==LocalDate.now()) "Hoje" else parsed.format(DateTimeFormatter.ofPattern("EEEE",pt)).replaceFirstChar {it.titlecase(pt)},fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                        HorizontalDivider(color=KoiColors.Blue.copy(alpha=.2f))
                        Text(entries.lastOrNull{it.role=="user"}?.content ?: entries.last().content,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis,color=KoiColors.Muted,fontSize=12.sp)
                        Text("${entries.size} mensagens →",color=KoiColors.Blue,fontSize=11.sp)
                    }
                    }
                    if(pair.size==1)Spacer(Modifier.weight(1f))
                }
            }
            item { Text("Seus relatórios e lembranças ficam nos atalhos acima.",color=KoiColors.Muted,fontSize=12.sp) }
        } else {
            item { TextButton(onClick=onBack) {Text("← Todos os dias")}; KoiChip("Registro original",KoiColors.Blue) }
            item { JournalPanel(day,messages) }
            items(messages.filter{it.localDate==day},key={it.id}) { message ->
                KoiPanel(Modifier.fillMaxWidth(),accent=if(message.role=="user") KoiColors.Purple else KoiColors.Blue) {
                    Eyebrow("${messageTime(message)}  /  ${if(message.role=="user") "VOCÊ" else "KOIWAI"}")
                    Text(message.content,fontSize=15.sp,lineHeight=23.sp)
                    if(message.status==MessageStatus.FAILED) Text("Falha no envio",color=KoiColors.Red,fontSize=11.sp)
                    if(memories!=null && message.role=="user" && message.status==MessageStatus.SENT && message.synced) {
                        TextButton(onClick={remembering=message;factText=message.content.take(500);factCategory="note"}) {Text("Guardar lembrança")}
                    }
                }
            }
        }
    }
    remembering?.let { message ->
        val memoryBusy=memories?.busy?.collectAsState()?.value ?: false
        val memoryInfo=memories?.info?.collectAsState()?.value
        MemoryEditor(factText,{factText=it},factCategory,{factCategory=it},{remembering=null},saving=memoryBusy,info=memoryInfo) {
            memories?.save(factText,factCategory,sourceId=message.id,onSaved={remembering=null;onFacts()})
        }
    }
}

private data class RoutineArea(val name:String,val icon:String,val subtitle:String,val accent:Color)
private val areas=listOf(
    RoutineArea("Tarefas","tasks","Um passo de cada vez.",KoiColors.Red),
    RoutineArea("Meu ritmo","memory","Hábitos, momentos e carinho.",KoiColors.Purple),
    RoutineArea("Agenda","agenda","Tempo para o que importa.",KoiColors.Blue),
    RoutineArea("Notas","notes","Dê espaço às suas ideias.",KoiColors.Purple),
    RoutineArea("Treinos","training","Sua evolução em movimento.",KoiColors.Purple),
    RoutineArea("Finanças","finance","Clareza para suas escolhas.",KoiColors.Blue),
    RoutineArea("Contas","finance","Seus vencimentos em ordem.",KoiColors.Red),
    RoutineArea("Orçamento","finance","Um limite para este mês.",KoiColors.Blue),
    RoutineArea("Diário","memory","Seu dia, do seu jeito.",KoiColors.Purple),
    RoutineArea("Listas","notes","Cada item é uma conquista.",KoiColors.Blue),
    RoutineArea("Metas","tasks","Seus planos ganham forma.",KoiColors.Red),
    RoutineArea("Hábitos","training","Constância sem cobrança.",KoiColors.Purple),
    RoutineArea("Ferramentas","spark","Calcular, pesquisar e compartilhar.",KoiColors.Blue))

@Composable
private fun RoutineScreen(group:String,onGroup:(String)->Unit,onCategory:(String)->Unit) {
    val groups=mapOf(
        "Dia a dia" to listOf("Agenda","Meu ritmo","Hábitos","Listas"),
        "Pessoal" to listOf("Notas","Diário","Metas","Treinos"),
        "Dinheiro" to listOf("Finanças","Contas","Orçamento"),
        "Conexões" to listOf("Ferramentas"))
    val accent=when(group){"Dinheiro","Conexões"->KoiColors.Blue;"Pessoal"->KoiColors.Purple;else->KoiColors.Red}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {KoiPageHeading("PAINEL DE MISSÕES","Seu próximo passo.","Um lugar para organizar o que importa.",KoiColors.Red,"tasks")}
        item {KoiPanel(Modifier.fillMaxWidth(),KoiColors.Red,{onCategory("Tarefas")}){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Eyebrow("COMECE POR AQUI",KoiColors.Red)
                    Text("Minhas tarefas",fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Text("Planejar, acompanhar e concluir.",fontSize=13.sp,color=KoiColors.Muted)
                }
                OrbitEmblem("tasks",KoiColors.Red,Modifier.size(72.dp))
            }
        }}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            groups.keys.forEach{label->FilterChip(selected=group==label,onClick={onGroup(label)},label={Text(label)})}
        }}
        items(areas.filter{it.name in groups.getValue(group)}.chunked(2)){pair ->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                pair.forEach{area->KoiPanel(Modifier.weight(1f),area.accent,{onCategory(area.name)}){
                    KoiGlyph(area.icon,area.accent,Modifier.size(30.dp))
                    Text(area.name,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text(area.subtitle,fontSize=12.sp,lineHeight=18.sp,color=KoiColors.Muted)
                }}
            }
        }
        item {KoiPanel(Modifier.fillMaxWidth(),accent){
            Eyebrow("COM A KOI",accent)
            Text(when(group){"Dinheiro"->"Mais clareza para suas escolhas.";"Pessoal"->"Seu mundo também merece espaço.";"Conexões"->"Tudo conectado, no seu controle.";else->"Pequenos passos também contam."},fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        }}
    }
}

@Composable
private fun SettingsScreen(onAccount:()->Unit,onTools:()->Unit,onFacts:()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val voice=com.thiago.assistentepessoal.tools.rememberKoiVoice()
    val account by app.auth.account.collectAsState()
    val prefs=LocalContext.current.getSharedPreferences("koiwai-preferences",0)
    var treatment by rememberSaveable {mutableStateOf(prefs.getString("treatment","Mestre") ?: "Mestre")}
    var reduced by remember {mutableStateOf(prefs.getBoolean("reduce-motion",false))}
    var speak by remember {mutableStateOf(prefs.getBoolean("speak-replies",false))}
    var capture by remember {mutableStateOf(prefs.getBoolean("capture-reports",false))}
    var saved by remember {mutableStateOf(false)}
    var detail by rememberSaveable {mutableStateOf<String?>(null)}
    var section by rememberSaveable {mutableStateOf<String?>(null)}
    val settingsList=rememberLazyListState()
    LaunchedEffect(section){settingsList.scrollToItem(0)}
    BackHandler(enabled=section!=null){section=null}
    LazyColumn(Modifier.fillMaxSize().imePadding(),state=settingsList,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {KoiPageHeading("CENTRAL DA KOI",section ?: "Do seu jeito.",if(section==null) "Escolha uma área. O restante fica fora do caminho." else "Ajuste o que faz diferença para você.",KoiColors.Blue,"settings")}
        if(section!=null) item {TextButton(onClick={section=null}){Text("← Todas as configurações")}}
        if(section==null) {
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue,onClick=onAccount) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Image(painterResource(R.drawable.koiwai),null,Modifier.size(58.dp).clip(CircleShape).background(KoiColors.Purple.copy(alpha=.15f)))
                    Column(Modifier.weight(1f)) { Text(if(account==null) "Entrar / criar conta" else "Conta e sincronização",fontWeight=FontWeight.SemiBold);Text(if(account==null) "Conecte sua história" else "Sua história acompanha você",color=KoiColors.Muted,fontSize=12.sp) }
                    KoiGlyph("arrow",KoiColors.Blue,Modifier.size(20.dp))
                }
            }
        }
            items(listOf(
                Triple("Aparência e personalidade","Movimento e como a Koi chama você","spark"),
                Triple("Voz e assistente","Falar com a Koi e chamar pelo Android","chat"),
                Triple("Lembretes e carinho","Missões, rotina e relatórios","agenda"),
                Triple("Conversa e memória","Relatos e lembranças confirmadas","memory"),
                Triple("Conexões e privacidade","Google, celular e permissões","settings"),
                Triple("Consumo e testes","Pontos de IA e dados fictícios","finance"),
                Triple("Sobre a Koi","Versão e informações do aplicativo","notes")
            )){(title,subtitle,icon)->KoiMenuRow(title,subtitle,icon,if(icon=="agenda")KoiColors.Red else KoiColors.Blue){section=title}}
        }
        if(section=="Aparência e personalidade") {
        item {
            Eyebrow("APARÊNCIA • V${BuildConfig.VERSION_NAME}")
            KoiPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Reduzir movimento",fontWeight=FontWeight.SemiBold);Text("Fundos estáticos e transições diretas",color=KoiColors.Muted,fontSize=12.sp) }
                    Switch(checked=reduced,onCheckedChange={reduced=it;prefs.edit().putBoolean("reduce-motion",it).apply()},modifier=Modifier.semantics{contentDescription="Reduzir movimento"})
                }
            }
        }
        item {
            KoiPanel(Modifier.fillMaxWidth()) {
                Text("Como devo chamar você?",fontWeight=FontWeight.SemiBold)
                OutlinedTextField(treatment,{treatment=it;saved=false},label={Text("Tratamento na saudação")},singleLine=true,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth())
                Text("Até 30 caracteres • apenas neste celular",fontSize=11.sp,color=KoiColors.Muted)
                KoiAction(if(saved) "Preferência salva ✓" else "Salvar preferência",{
                    treatment=treatment.trim();prefs.edit().putString("treatment",treatment).apply();saved=true
                },Modifier.fillMaxWidth(),treatment.trim().isNotEmpty() && treatment.trim().length<=30)
            }
        }
        }
        if(section=="Voz e assistente") {
        item {com.thiago.assistentepessoal.tools.AssistantRolePanel()}
        item {com.thiago.assistentepessoal.tools.VoiceCustomizationPanel(voice)}
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple) {
                Eyebrow("VOZ DA KOI",KoiColors.Purple)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("Ouvir novas respostas");Text("Enquanto o chat está aberto",fontSize=12.sp,color=KoiColors.Muted)}
                    Switch(checked=speak,onCheckedChange={speak=it;prefs.edit().putBoolean("speak-replies",it).apply()})
                }
                Text("Escolha a voz em português no seu celular.",color=KoiColors.Muted,fontSize=12.sp)
                KoiAction("Testar voz",{voice.speak("Oi! Eu sou a Koi. Vamos cuidar do seu dia juntas? 💜")},Modifier.fillMaxWidth(),voice.ready)
                Row {
                    TextButton(onClick={com.thiago.assistentepessoal.tools.openIntent(context,android.content.Intent("com.android.settings.TTS_SETTINGS"))}){Text("Configurações da voz do Android")}
                    TextButton(onClick={voice.stop()}){Text("Parar voz")}
                }
                voice.info?.let{Text(it,color=KoiColors.Muted,fontSize=12.sp)}
            }
        }
        }
        if(section=="Lembretes e carinho") {
        item {com.thiago.assistentepessoal.routine.ReminderSettings()}
        item {com.thiago.assistentepessoal.routine.LifeReminderSettings()}
        item {ReportSettings()}
        }
        if(section=="Conversa e memória") {
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
                Eyebrow("CONVERSA E MEMÓRIA",KoiColors.Blue)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("Guardar relatos do dia");Text("Preferências, acontecimentos e dinheiro informado",fontSize=12.sp,color=KoiColors.Muted)}
                    Switch(checked=capture,onCheckedChange={capture=it;prefs.edit().putBoolean("capture-reports",it).apply()})
                }
                Text("Opcional. A Koi pode registrar relatos claros sem você dizer salve. Confira o resultado no chat e use Desfazer se precisar. Diário aparece em Notas; não lê sua conta bancária.",fontSize=12.sp,color=KoiColors.Muted)
            }
        }
        }
        if(section=="Conexões e privacidade") {
        item {com.thiago.assistentepessoal.tools.GooglePrivacySettings()}
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue) {
                Eyebrow("ATALHOS DA SUA KOI",KoiColors.Blue)
                KoiAction("Ferramentas e celular",onTools,Modifier.fillMaxWidth())
                KoiAction("Gerenciar lembranças",onFacts,Modifier.fillMaxWidth())
                TextButton(onClick={com.thiago.assistentepessoal.tools.openIntent(context,android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}){Text("Permissões e acessos do celular")}
                TextButton(onClick={com.thiago.assistentepessoal.tools.openIntent(context,android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Permissões de notificações")}
            }
        }
        }
        if(section=="Consumo e testes") {
        item {
            PoeUsagePanel()
        }
        item {
            DemoDataPanel()
        }
        }
        if(section=="Sobre a Koi") {
        item {Eyebrow("SOBRE SUA KOI")}
        items(listOf("Voz e notificações","Memória e privacidade","Sobre a Koiwai")) { title ->
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue,onClick={detail=title}) {
                Row(verticalAlignment=Alignment.CenterVertically) {Text(title,modifier=Modifier.weight(1f),fontSize=14.sp);KoiGlyph("arrow",KoiColors.Muted,Modifier.size(18.dp))}
            }
        }
        item {Text("KOIWAI  /  FEITA PARA ACOMPANHAR VOCÊ",fontSize=9.sp,letterSpacing=1.sp,color=KoiColors.Muted,modifier=Modifier.padding(vertical=8.dp))}
        }
    }
    detail?.let { title ->
        val description=when(title) {
            "Voz e notificações"->"No Chat, Voz transforma sua fala em texto para revisar antes de enviar. Ouvir lê uma resposta; ative Ouvir novas respostas para leitura automática enquanto o chat estiver aberto. Configure e teste a voz acima. Lembretes precisam de data, horário e permissão de notificações; o Android pode atrasá-los. Ativação por “Koi” ainda não está disponível."
            "Memória e privacidade"->"Você pode revisar, editar e apagar lembranças confirmadas na área Memória. Conversas, lembranças e tarefas ficam separadas por conta. O chat envia o pedido, parte da conversa recente, lembranças confirmadas e uma lista limitada de tarefas ao provedor de IA. Não há limpeza automática. Exportação e exclusão completa ainda estão em preparação."
            else->"Koi é sua assistente pessoal. Esta versão ${BuildConfig.VERSION_NAME} reúne tarefas, agenda, hábitos, notas, listas, metas, treinos e finanças, além de memória, relatórios, voz, imagens e pesquisa. Conversas ficam no celular e no Supabase quando sincronizadas; fotos enviadas ficam no histórico deste celular. O servidor gratuito pode demorar para despertar após ficar sem uso."
        }
        AlertDialog(onDismissRequest={detail=null},title={Text(title)},text={Text(description)},confirmButton={TextButton(onClick={detail=null}){Text("Entendi")}})
    }
}
