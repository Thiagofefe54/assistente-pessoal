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

@Composable internal fun GoogleReadResults(data:JSONObject){
    val context=LocalContext.current
    val email=data.getJSONObject("account").getString("email")
    val service=data.optString("service")
    val items=data.getJSONArray("items")
    var filter by remember(data.toString()){mutableStateOf("Todas")}
    var details by remember(data.toString()){mutableStateOf(false)}
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
        if(details){
            listOf("date","modifiedTime","mimeType").forEach{key->item.optString(key).takeIf{it.isNotBlank()}?.let{Text(it,fontSize=11.sp,color=KoiColors.Muted)}}
            googleItemUrl(service,email,item.optString("id"))?.let{url->TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse(url)))}){
                Text(if(service=="calendar" || service=="task_items")"Abrir serviço Google" else "Abrir item no Google")
            }}
        }
    }
    if(data.optBoolean("partial"))Text("Consulta parcial: existem itens fora deste recorte.",fontSize=12.sp,color=KoiColors.Muted)
    data.optString("note").takeIf{it.isNotBlank()}?.let{Text(it,fontSize=11.sp,color=KoiColors.Muted)}
    data.optJSONObject("plan")?.let{GooglePlanDetails(it)}
}
