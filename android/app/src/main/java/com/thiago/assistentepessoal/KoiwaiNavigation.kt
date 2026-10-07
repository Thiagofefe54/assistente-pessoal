package com.thiago.assistentepessoal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.chat.*
import com.thiago.assistentepessoal.cloud.AccountScreen
import com.thiago.assistentepessoal.memory.*
import com.thiago.assistentepessoal.routine.TasksScreen
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
        outline=Color(0xFF514262))) {
        CompositionLocalProvider(LocalContentColor provides Color.White,content=content)
    }
}

@Composable
fun KoiwaiNavigation(taskRequest:Int=0) {
    var selected by rememberSaveable { mutableStateOf("home") }
    var accountReturn by rememberSaveable { mutableStateOf("settings") }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val states = rememberSaveableStateHolder()
    val app = LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val motion = LocalKoiMotion.current
    LaunchedEffect(account?.id) { day=null }
    LaunchedEffect(taskRequest) { if(taskRequest>0) {selected="routine";category="Tarefas";day=null} }
    fun back() { when {
        selected=="account" -> selected=accountReturn
        selected=="facts" -> selected="memory"
        selected=="memory" && day!=null -> day=null
        selected=="routine" && category!=null -> category=null
        else -> selected="home"
    } }
    fun openAccount(from: String) { accountReturn=from; selected="account" }
    BackHandler(enabled=selected!="home") { back() }
    Box(Modifier.fillMaxSize()) {
        KoiBackdrop(selected)
        Scaffold(containerColor=Color.Transparent, bottomBar={
            if(selected!="account") Surface(color=KoiColors.Ink.copy(alpha=.95f),shadowElevation=12.dp) {
                Column {
                    HorizontalDivider(color=KoiColors.Purple.copy(alpha=.18f))
                    NavigationBar(containerColor=Color.Transparent,tonalElevation=0.dp) {
                        tabs.forEach { (route,label) ->
                            val active=selected==route || (route=="memory" && selected=="facts")
                            NavigationBarItem(selected=active,onClick={ selected=route;day=null;category=null },
                                icon={ KoiGlyph(route,if(active) KoiColors.Purple else KoiColors.Muted) },
                                label={ Text(label,fontSize=11.sp,fontWeight=if(active) FontWeight.Bold else FontWeight.Normal) },
                                colors=NavigationBarItemDefaults.colors(selectedTextColor=Color.White,unselectedTextColor=KoiColors.Muted,indicatorColor=KoiColors.Purple.copy(alpha=.16f)))
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
                            "home" -> HomeScreen({selected="chat"},{selected="routine"},{openAccount("home")})
                            "chat" -> ChatScreen({back()},{openAccount("chat")},{day=it;selected="memory"})
                            "memory" -> MemoryScreen(day,{day=it},{day=null},{selected="facts"})
                            "facts" -> MemoriesScreen({selected="memory"},{openAccount("facts")})
                            "routine" -> if(category=="Tarefas") TasksScreen({category=null},{openAccount("routine")}) else RoutineScreen(category,{category=it},{category=null})
                            "settings" -> SettingsScreen {openAccount("settings")}
                            "account" -> AccountScreen {back()}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onChat: () -> Unit, onRoutine: () -> Unit, onAccount: () -> Unit) {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val tasksRepo by app.tasks.collectAsState()
    val tasks=tasksRepo?.tasks?.collectAsState()?.value
    LaunchedEffect(tasksRepo) {tasksRepo?.refresh()}
    val prefs=LocalContext.current.getSharedPreferences("koiwai-preferences",0)
    val treatment=prefs.getString("treatment","Mestre") ?: "Mestre"
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while(true){now=ZonedDateTime.now();delay(30000)} }
    val greeting=when(now.hour){in 5..11->"Bom dia";in 12..17->"Boa tarde";else->"Boa noite"}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("SEU UNIVERSO PESSOAL",KoiColors.Purple)
                Spacer(Modifier.height(7.dp))
                Text("$greeting, $treatment.",fontSize=24.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold)
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm • dd 'de' MMMM",pt)),color=KoiColors.Muted,fontSize=12.sp)
            }
            KoiChip(if(account==null) "Local" else "Conta",KoiColors.Blue)
        }
        Box(Modifier.fillMaxWidth().height(190.dp),contentAlignment=Alignment.Center) {
            OrbitEmblem("spark",KoiColors.Purple,Modifier.size(190.dp))
            Image(painterResource(R.drawable.koiwai),contentDescription="Koiwai",modifier=Modifier.size(180.dp))
        }
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Koiwai",fontSize=25.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
            Text("Sua assistente pessoal",color=KoiColors.Muted,fontSize=13.sp)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            KoiPanel(Modifier.weight(1f),accent=KoiColors.Purple) {
                Eyebrow("CLIMA",KoiColors.Purple)
                Text("— °C",fontSize=27.sp,fontWeight=FontWeight.Light)
                Text("Ainda não configurado",color=KoiColors.Muted,fontSize=11.sp)
            }
            KoiPanel(Modifier.weight(1f),accent=KoiColors.Blue,onClick=onAccount) {
                Eyebrow("SUA CONTA",KoiColors.Blue)
                Text(if(account==null) "Local" else "Conectada",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                Text("Conta e sincronização →",color=KoiColors.Muted,fontSize=11.sp)
            }
        }
        KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Red,onClick=onRoutine) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                KoiGlyph("routine",KoiColors.Red)
                Text("Próximas tarefas",fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).padding(start=10.dp))
                KoiGlyph("arrow",KoiColors.Muted,Modifier.size(20.dp))
            }
            Text(if(account==null) "Entre na sua conta para organizar suas missões."
                else if(tasks==null) "Abra Rotina para conferir suas tarefas."
                else "${tasks.count {it.completedAt==null}} pendentes • ${tasks.count {it.overdue()}} vencidas",color=KoiColors.Muted,fontSize=13.sp)
        }
        KoiAction("✦  Conversar comigo",onChat,Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MemoryScreen(day: String?, onDay: (String)->Unit, onBack: ()->Unit, onFacts: ()->Unit) {
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
            Eyebrow("ARQUIVO PESSOAL",KoiColors.Blue)
            Spacer(Modifier.height(8.dp))
            Text(if(day==null) "Cada dia,\num capítulo." else dayLabel(day),fontSize=32.sp,lineHeight=37.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Suas conversas, organizadas por dia.",color=KoiColors.Muted,fontSize=13.sp)
        }
        if(day==null) {
            item {
                KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Purple,onClick=onFacts) {
                    Eyebrow("O QUE A KOI LEVA COM ELA",KoiColors.Purple)
                    Text("Lembranças confirmadas →",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                    Text("Preferências, objetivos e detalhes que você escolhe guardar.",color=KoiColors.Muted,fontSize=13.sp)
                }
            }
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
            items(days.keys.toList(),key={it}) { date ->
                val entries=days.getValue(date)
                val parsed=LocalDate.parse(date)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(42.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(parsed.dayOfMonth.toString().padStart(2,'0'),fontSize=26.sp,fontWeight=FontWeight.Light,color=Color.White)
                        Text(parsed.format(DateTimeFormatter.ofPattern("MMM",pt)).uppercase(pt),fontSize=10.sp,color=KoiColors.Blue,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(1.dp).height(65.dp).background(KoiColors.Blue.copy(alpha=.28f)))
                    }
                    KoiPanel(Modifier.weight(1f),accent=KoiColors.Blue,onClick={onDay(date)}) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(parsed==LocalDate.now()) "Hoje" else parsed.format(DateTimeFormatter.ofPattern("EEEE",pt)).replaceFirstChar {it.titlecase(pt)},fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                            KoiGlyph("arrow",KoiColors.Blue,Modifier.size(18.dp))
                        }
                        Text(entries.lastOrNull{it.role=="user"}?.content ?: entries.last().content,maxLines=2,overflow=TextOverflow.Ellipsis,color=KoiColors.Muted,fontSize=13.sp)
                        Text("${entries.size} mensagens • ${parsed.year}",color=KoiColors.Blue,fontSize=11.sp)
                    }
                }
            }
            item { Text("Relatórios periódicos chegam em uma próxima etapa. Suas lembranças já podem ser revisadas acima.",color=KoiColors.Muted,fontSize=12.sp) }
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
    RoutineArea("Agenda","agenda","Tempo para o que importa.",KoiColors.Blue),
    RoutineArea("Notas","notes","Dê espaço às suas ideias.",KoiColors.Purple),
    RoutineArea("Treinos","training","Sua evolução em movimento.",KoiColors.Purple),
    RoutineArea("Finanças","finance","Clareza para suas escolhas.",KoiColors.Blue))

@Composable
private fun RoutineScreen(category:String?,onCategory:(String)->Unit,onBack:()->Unit) {
    val area=areas.find{it.name==category}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Eyebrow("PAINEL DE MISSÕES",KoiColors.Red)
            Spacer(Modifier.height(8.dp))
            Text(area?.name ?: "Seu próximo\npasso.",fontSize=34.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(area?.subtitle ?: "Pequenas ações. Novas possibilidades.",color=KoiColors.Muted)
        }
        if(area!=null) {
            item { TextButton(onClick=onBack){Text("← Todas as categorias")} }
            item {
                KoiPanel(Modifier.fillMaxWidth(),accent=area.accent) {
                    OrbitEmblem(area.icon,area.accent,Modifier.size(150.dp).align(Alignment.CenterHorizontally))
                    KoiChip("Em preparação",area.accent)
                    Text("Seu espaço está tomando forma.",fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                    Text("O cadastro e a sincronização de ${area.name.lowercase(pt)} chegam na próxima etapa deste módulo.",color=KoiColors.Muted,lineHeight=23.sp)
                }
            }
        } else {
            item {
                KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Red,onClick={onCategory("Tarefas")}) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Eyebrow("SEU FOCO",KoiColors.Red); Spacer(Modifier.height(10.dp)); Text("Tarefas",fontSize=26.sp,fontWeight=FontWeight.Bold);Text(areas[0].subtitle,color=KoiColors.Muted,fontSize=13.sp) }
                        OrbitEmblem("tasks",KoiColors.Red,Modifier.size(88.dp))
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){KoiChip("Suas missões",KoiColors.Red);KoiGlyph("arrow",KoiColors.Red)}
                }
            }
            items(areas.drop(1).chunked(2)) { pair ->
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    pair.forEach { item ->
                        KoiPanel(Modifier.weight(1f).heightIn(min=178.dp),accent=item.accent,onClick={onCategory(item.name)}) {
                            KoiGlyph(item.icon,item.accent,Modifier.size(30.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(item.name,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                            Text(item.subtitle,color=KoiColors.Muted,fontSize=12.sp,lineHeight=17.sp)
                            Text("Em preparação",color=item.accent,fontSize=10.sp)
                        }
                    }
                }
            }
            item { Text("A Koi vai ajudar você a acompanhar tudo por aqui.",fontSize=12.sp,color=KoiColors.Muted) }
        }
    }
}

@Composable
private fun SettingsScreen(onAccount:()->Unit) {
    val app=LocalContext.current.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState()
    val prefs=LocalContext.current.getSharedPreferences("koiwai-preferences",0)
    var treatment by rememberSaveable {mutableStateOf(prefs.getString("treatment","Mestre") ?: "Mestre")}
    var reduced by remember {mutableStateOf(prefs.getBoolean("reduce-motion",false))}
    var saved by remember {mutableStateOf(false)}
    var detail by rememberSaveable {mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Eyebrow("CONFIGURAÇÕES",KoiColors.Purple)
            Spacer(Modifier.height(8.dp))
            Text("Seu universo.\nDo seu jeito.",fontSize=32.sp,lineHeight=37.sp,fontWeight=FontWeight.Bold)
        }
        item {
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue,onClick=onAccount) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Image(painterResource(R.drawable.koiwai),null,Modifier.size(58.dp).clip(CircleShape).background(KoiColors.Purple.copy(alpha=.15f)))
                    Column(Modifier.weight(1f)) { Text(if(account==null) "Entrar / criar conta" else "Conta e sincronização",fontWeight=FontWeight.SemiBold);Text(if(account==null) "Conecte sua história" else "Sua história acompanha você",color=KoiColors.Muted,fontSize=12.sp) }
                    KoiGlyph("arrow",KoiColors.Blue,Modifier.size(20.dp))
                }
            }
        }
        item {
            Eyebrow("EXPERIÊNCIA")
            Spacer(Modifier.height(10.dp))
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
        item {com.thiago.assistentepessoal.routine.ReminderSettings()}
        item {Eyebrow("SOBRE SUA KOI")}
        items(listOf("Voz e notificações","Memória e privacidade","Sobre a Koiwai")) { title ->
            KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue,onClick={detail=title}) {
                Row(verticalAlignment=Alignment.CenterVertically) {Text(title,modifier=Modifier.weight(1f),fontSize=14.sp);KoiGlyph("arrow",KoiColors.Muted,Modifier.size(18.dp))}
            }
        }
        item {Text("KOIWAI  /  FEITA PARA ACOMPANHAR VOCÊ",fontSize=9.sp,letterSpacing=1.sp,color=KoiColors.Muted,modifier=Modifier.padding(vertical=8.dp))}
    }
    detail?.let { title ->
        val description=when(title) {
            "Voz e notificações"->"Lembretes de tarefas podem ser ativados acima. Precisam de data e horário; o Android pode atrasá-los. Voz por botão e respostas faladas ainda estão em preparação. A ativação por “Koi” será testada depois."
            "Memória e privacidade"->"Você pode revisar, editar e apagar lembranças confirmadas na área Memória. Conversas, lembranças e tarefas ficam separadas por conta. O chat envia o pedido, parte da conversa recente, lembranças confirmadas e uma lista limitada de tarefas ao provedor de IA. Não há limpeza automática. Exportação e exclusão completa ainda estão em preparação."
            else->"Koi é sua assistente pessoal. O chat usa IA na nuvem e suas conversas ficam no celular e no Supabase quando sincronizadas. O servidor gratuito pode levar um pouco para despertar após ficar sem uso. Tarefas, lembranças e resumos diários já estão disponíveis; as demais áreas continuam em preparação."
        }
        AlertDialog(onDismissRequest={detail=null},title={Text(title)},text={Text(description)},confirmButton={TextButton(onClick={detail=null}){Text("Entendi")}})
    }
}
