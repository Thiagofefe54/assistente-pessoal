package com.thiago.assistentepessoal.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import java.time.LocalDate
import java.util.UUID
import com.thiago.assistentepessoal.tools.shareText

@Composable
fun PersonalScreen(category:String,onBack:()->Unit,onAccount:()->Unit){
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val repository by app.personal.collectAsState()
    val kinds=when(category){"Listas"->listOf("list");"Metas"->listOf("goal");"Treinos"->listOf("workout");"Finanças"->listOf("expense","income");else->listOf("note")}
    val accent=when(category){"Finanças"->KoiColors.Blue;"Treinos"->KoiColors.Red;else->KoiColors.Purple}
    val repo=repository
    if(repo==null){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        TextButton(onClick=onBack){Text("← Rotina")};Text(category,fontSize=30.sp)
        Text("Entre na sua conta para guardar e sincronizar seus registros.");KoiAction("Minha conta",onAccount)
    };return}
    val records by repo.records.collectAsState();val busy by repo.busy.collectAsState();val info by repo.info.collectAsState();val undo by repo.undo.collectAsState()
    var archived by rememberSaveable{mutableStateOf(false)}
    var query by rememberSaveable{mutableStateOf("")}
    var monthOnly by rememberSaveable{mutableStateOf(true)}
    var editor by remember{mutableStateOf(false)};var editing by remember{mutableStateOf<PersonalRecord?>(null)}
    var creationId by rememberSaveable{mutableStateOf(UUID.randomUUID().toString())}
    LaunchedEffect(repo){repo.refresh()}
    val month=LocalDate.now().toString().take(7)
    val visible=records.orEmpty().filter{it.kind in kinds && it.archived==archived &&
        (query.isBlank() || it.title.contains(query,true) || it.content.contains(query,true)) &&
        (category!="Finanças" || !monthOnly || it.date?.startsWith(month)==true)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            TextButton(onClick=onBack){Text("← Rotina")};Eyebrow("SEU ESPAÇO",accent);Text(category,fontSize=32.sp)
            Text(when(category){"Listas"->"Um item de cada vez.";"Metas"->"Transforme planos em progresso.";"Treinos"->"Registre o que você fez e acompanhe a evolução.";"Finanças"->"Receitas e despesas registradas por você, em reais.";else->"Ideias que acompanham você."},color=KoiColors.Muted)
            Spacer(Modifier.height(12.dp))
            KoiAction("＋ Novo registro",{editing=null;creationId=UUID.randomUUID().toString();editor=true},Modifier.fillMaxWidth(),!busy && records!=null && records!!.size<200)
            OutlinedTextField(query,{query=it},label={Text("Buscar")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row{FilterChip(selected=!archived,onClick={archived=false},label={Text("Ativos")});Spacer(Modifier.width(8.dp));FilterChip(selected=archived,onClick={archived=true},label={Text("Arquivados")})}
            if(category=="Finanças")Row{FilterChip(selected=monthOnly,onClick={monthOnly=true},label={Text("Este mês")});Spacer(Modifier.width(8.dp));FilterChip(selected=!monthOnly,onClick={monthOnly=false},label={Text("Todos")})}
            TextButton(onClick={repo.refresh()},enabled=!busy){Text("Atualizar")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=accent)
            info?.let{Text(it,color=KoiColors.Blue)}
            if(undo!=null)TextButton(onClick={repo.undoLast()},enabled=!busy){Text("↶ Desfazer última ação")}
        }
        if(category=="Finanças" && records!=null)item{KoiPanel(Modifier.fillMaxWidth(),accent=accent){
            val income=visible.filter{it.kind=="income"}.sumOf{it.cents ?: 0L};val expense=visible.filter{it.kind=="expense"}.sumOf{it.cents ?: 0L}
            Text("Receitas: ${money(income)}");Text("Despesas: ${money(expense)}");Text("Diferença: ${money(income-expense)}",fontSize=22.sp)
            Text("Totais dos registros e filtros desta tela. Não é seu saldo bancário.",color=KoiColors.Muted,fontSize=12.sp)
        }}
        if(records!=null && visible.isEmpty())item{Text("Nenhum registro neste filtro. Você pode criar um acima ou pedir pelo chat.",color=KoiColors.Muted)}
        items(visible,key={it.id}){record->KoiPanel(Modifier.fillMaxWidth(),accent=accent){
            Eyebrow(personalKinds[record.kind] ?: category,accent);Text(record.title,fontSize=21.sp)
            if(record.kind=="list")record.content.lines().filter{it.isNotBlank()}.forEachIndexed{index,line->
                val checked=line.startsWith("[x]",true)
                Row{
                    Checkbox(checked=checked,enabled=!busy && !record.archived,onCheckedChange={done->
                        val lines=record.content.lines().toMutableList()
                        val original=record.content.lines().indices.filter{record.content.lines()[it].isNotBlank()}[index]
                        lines[original]=(if(done)"[x] " else "[ ] ")+line.removePrefix("[ ]").removePrefix("[x]").removePrefix("[X]").trim()
                        repo.save(record.kind,record.title,lines.joinToString("\n"),record.cents,record.progress,record.date,record)
                    })
                    Text(line.removePrefix("[ ]").removePrefix("[x]").removePrefix("[X]").trim(),modifier=Modifier.weight(1f).padding(top=12.dp))
                }
            }else if(record.content.isNotBlank())Text(record.content,color=KoiColors.Muted)
            if(record.kind=="goal"){LinearProgressIndicator(progress={record.progress/100f},modifier=Modifier.fillMaxWidth(),color=accent);Text("${record.progress}% concluída")}
            record.cents?.let{Text(money(it),fontSize=22.sp,color=if(record.kind=="expense")KoiColors.Red else KoiColors.Blue)}
            record.date?.let{Text(it,color=KoiColors.Muted,fontSize=12.sp)}
            Row{TextButton(onClick={editing=record;editor=true},enabled=!busy){Text("Editar")};TextButton(onClick={repo.archive(record)},enabled=!busy){Text(if(record.archived)"Recuperar" else "Arquivar")}
                TextButton(onClick={shareText(context,record.title,listOfNotNull(record.content.takeIf{it.isNotBlank()},record.cents?.let{money(it)},record.date,if(record.kind=="goal")"Progresso: ${record.progress}%" else null).joinToString("\n"))}){Text("Compartilhar")}
            }
        }}
    }
    if(editor)key(creationId,editing?.id){
        var kind by rememberSaveable{mutableStateOf(editing?.kind ?: kinds.first())}
        var title by rememberSaveable{mutableStateOf(editing?.title ?: "")};var content by rememberSaveable{mutableStateOf(editing?.content ?: "")}
        var amount by rememberSaveable{mutableStateOf(editing?.cents?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()} ?: "")}
        var progress by rememberSaveable{mutableStateOf((editing?.progress ?: 0).toString())}
        var date by rememberSaveable{mutableStateOf(editing?.date ?: if(category in listOf("Finanças","Treinos"))LocalDate.now().toString() else "")}
        val valid=title.isNotBlank() && title.length<=160 && content.length<=8000 &&
            (kind !in listOf("expense","income") || parseCents(amount)!=null) &&
            ((progress.toIntOrNull() ?: -1) in 0..100) && (date.isBlank() || runCatching{LocalDate.parse(date)}.isSuccess)
        AlertDialog(onDismissRequest={if(!busy)editor=false},title={Text(if(editing==null)"Novo registro" else "Editar registro")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(kinds.size>1 && editing==null)Row{kinds.forEach{type->FilterChip(selected=kind==type,onClick={kind=type},label={Text(personalKinds[type] ?: type)})}}
                OutlinedTextField(title,{title=it},label={Text("Título")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(content,{content=it},label={Text(if(kind=="list")"Um item por linha" else "Detalhes")},minLines=3,modifier=Modifier.fillMaxWidth())
                if(kind in listOf("expense","income"))OutlinedTextField(amount,{amount=it},label={Text("Valor em reais, ex.: 12,50")},singleLine=true)
                if(kind=="goal")OutlinedTextField(progress,{progress=it},label={Text("Progresso de 0 a 100%")},singleLine=true)
                OutlinedTextField(date,{date=it},label={Text("Data opcional: AAAA-MM-DD")},singleLine=true)
                info?.let{Text(it,color=KoiColors.Blue)}
            }
        },confirmButton={TextButton(enabled=valid && !busy,onClick={repo.save(kind,title,content,if(kind in listOf("expense","income"))parseCents(amount) else null,progress.toInt(),date.ifBlank{null},editing,creationId){editor=false}}){Text(if(busy)"Salvando…" else "Salvar")}},dismissButton={TextButton(onClick={editor=false},enabled=!busy){Text("Cancelar")}})
    }
}
