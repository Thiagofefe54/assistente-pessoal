package com.thiago.assistentepessoal.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.thiago.assistentepessoal.*
import org.json.JSONObject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

/** Fixed Google destinations; provider URLs are never executed as arbitrary links. */
internal fun googleItemUrl(service:String,email:String,id:String):String?{
    val auth=java.net.URLEncoder.encode(email,"UTF-8")
    return when(service){
        "mail"->if(id.matches(Regex("[A-Za-z0-9_-]{1,200}")))"https://mail.google.com/mail/?authuser=$auth#all/$id" else null
        "drive"->if(id.matches(Regex("[A-Za-z0-9_-]{1,200}")))"https://drive.google.com/file/d/$id/view?authuser=$auth" else null
        "calendar"->"https://calendar.google.com/calendar/?authuser=$auth"
        "task_items"->"https://tasks.google.com/?authuser=$auth"
        else->null
    }
}

@Composable internal fun GoogleReadResults(data:JSONObject,onSelect:((String,String)->Unit)?=null){
    val context=LocalContext.current
    val app=context.applicationContext as KoiwaiApplication
    val account by app.auth.account.collectAsState();val owner=account?.id
    val scope=rememberCoroutineScope()
    val email=data.getJSONObject("account").getString("email")
    val service=data.optString("service")
    val items=data.getJSONArray("items")
    var filter by remember(data.toString()){mutableStateOf("Todas")}
    var details by remember(data.toString()){mutableStateOf(false)}
    var preview by remember(owner,data.toString()){mutableStateOf<JSONObject?>(null)}
    var error by remember(owner,data.toString()){mutableStateOf<String?>(null)}
    var reading by remember(owner,data.toString()){mutableStateOf(false)}
    Text(email,fontSize=14.sp,color=KoiColors.Blue)
    if(service=="task_items"){
        val completed=(0 until items.length()).count{items.getJSONObject(it).optString("status")=="completed"}
        Text("${items.length()-completed} pendentes · $completed concluídas neste recorte",fontSize=12.sp)
        Row{listOf("Todas","Pendentes","Concluídas").forEach{choice->TextButton(onClick={filter=choice}){Text(if(filter==choice)"✓ $choice" else choice)}}}
    }
    val visible=(0 until items.length()).filter{index->
        val done=items.getJSONObject(index).optString("status")=="completed"
        service!="task_items" || filter=="Todas" || (filter=="Concluídas")==done
    }
    if(visible.isEmpty())Text("Nenhum item encontrado neste recorte.",fontSize=12.sp)
    TextButton(onClick={details=!details}){Text(if(details)"Recolher detalhes" else "Detalhes e abrir no Google")}
    visible.forEach{index->
        val item=items.getJSONObject(index)
        Text((if(item.optString("status")=="completed")"✓ " else "• ")+item.optString("title",item.optString("summary",item.optString("name",item.optString("subject","Sem título")))),fontSize=14.sp)
        item.optJSONObject("start")?.let{Text(it.optString("dateTime",it.optString("date")),fontSize=12.sp,color=KoiColors.Muted)}
        listOf("due","list","from").forEach{key->item.optString(key).takeIf{it.isNotBlank()}?.let{Text(it,fontSize=12.sp,color=KoiColors.Muted)}}
        if(item.has("blocks_time") && !item.optBoolean("blocks_time"))Text("Não bloqueia horário",fontSize=11.sp,color=KoiColors.Muted)
        if(onSelect!=null && service in listOf("calendars","tasks"))TextButton(onClick={
            onSelect(if(service=="calendars")"calendar" else "task_items",item.getString("id"))
        }){Text(if(service=="calendars")"Consultar esta agenda" else "Consultar esta lista")}
        if(details){
            listOf("date","modifiedTime","mimeType").forEach{key->item.optString(key).takeIf{it.isNotBlank()}?.let{Text(it,fontSize=11.sp,color=KoiColors.Muted)}}
            googleItemUrl(service,email,item.optString("id"))?.let{url->TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse(url)))}){
                Text(if(service=="calendar" || service=="task_items")"Abrir serviço Google" else "Abrir item no Google")
            }}
            if(service in listOf("mail","drive"))TextButton(enabled=owner!=null && !reading,onClick={
                if(owner!=null){reading=true;error=null
                    scope.launch{try{
                        val result=assistantRequest(app,owner,"google-content",JSONObject().put("connection_id",data.getJSONObject("account").getString("id")).put("service",service).put("item_id",item.getString("id")),allowCached=false)
                        if(app.auth.account.value?.id==owner)preview=result
                    }catch(e:CancellationException){throw e}
                    catch(e:Exception){if(app.auth.account.value?.id==owner)error=e.message?:"Não consegui abrir este texto."}
                    finally{if(app.auth.account.value?.id==owner)reading=false}}
                }
            }){Text(if(reading)"Abrindo texto…" else "Ler texto na Koi")}
        }
    }
    error?.let{Text(it,fontSize=12.sp,color=KoiColors.Red)}
    preview?.let{value->AlertDialog(onDismissRequest={preview=null},title={Text(value.getString("title"))},text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){
            Text(value.getJSONObject("account").getString("email"),fontSize=12.sp,color=KoiColors.Blue)
            Text(value.getString("text").ifBlank{"Nenhum texto compatível disponível."},fontSize=14.sp)
            if(value.optBoolean("partial"))Text("Texto parcial: há conteúdo fora deste recorte.",fontSize=12.sp,color=KoiColors.Red)
            Text(value.getString("note"),fontSize=11.sp,color=KoiColors.Muted)
        }
    },confirmButton={TextButton(onClick={preview=null}){Text("Fechar texto")}})}
    if(data.optBoolean("partial"))Text("Consulta parcial: existem itens fora deste recorte.",fontSize=12.sp,color=KoiColors.Muted)
    data.optString("note").takeIf{it.isNotBlank()}?.let{Text(it,fontSize=11.sp,color=KoiColors.Muted)}
    data.optJSONObject("plan")?.let{GooglePlanDetails(it)}
}
