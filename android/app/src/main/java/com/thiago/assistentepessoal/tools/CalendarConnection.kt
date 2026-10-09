package com.thiago.assistentepessoal.tools

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

internal data class PhoneCalendarEvent(val id:Long,val title:String,val begin:Long,val end:Long,val allDay:Boolean)
internal fun phoneCalendarWeek(context:android.content.Context):List<PhoneCalendarEvent>{
    check(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED)
    val start=LocalDate.now().atStartOfDay(ZoneId.systemDefault());val finish=start.plusDays(7)
    val uri=CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(uri,start.toInstant().toEpochMilli());ContentUris.appendId(uri,finish.toInstant().toEpochMilli())
    val rows=mutableListOf<PhoneCalendarEvent>()
    context.contentResolver.query(uri.build(),arrayOf(CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.ALL_DAY),
        "visible = ?",arrayOf("1"),"begin ASC")?.use{c->
        while(c.moveToNext() && rows.size<20)rows+=PhoneCalendarEvent(c.getLong(0),(c.getString(1)?:"Sem título").take(160),c.getLong(2),c.getLong(3),c.getInt(4)==1)
    }
    return rows
}
@Composable fun CalendarConnectionPanel(){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var rows by remember{mutableStateOf<List<PhoneCalendarEvent>?>(null)}
    var info by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    fun load(){if(busy)return;busy=true;info=null;scope.launch{
        try{rows=withContext(Dispatchers.IO){phoneCalendarWeek(context)}}catch(e:CancellationException){throw e}catch(_:Exception){rows=null;info="Não consegui ler a agenda. Confira a permissão e a sincronização no aplicativo de calendário."}finally{busy=false}
    }}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)load()else info="Agenda não autorizada. Você pode continuar usando a Koi."}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Text("Agenda conectada ao celular",fontSize=20.sp)
        Text("Lê até 20 ocorrências nos próximos 7 dias, incluindo Google Agenda se estiver sincronizada no Android. Só consulta ao tocar. Eventos ficam neste painel; não são enviados à IA nem importados como tarefas.",fontSize=12.sp,color=KoiColors.Muted)
        KoiAction(if(busy)"Consultando…" else "Consultar agenda deste celular",{
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED)load()
            else permission.launch(Manifest.permission.READ_CALENDAR)
        },enabled=!busy)
        info?.let{Text(it,color=KoiColors.Red,fontSize=12.sp)}
        rows?.let{events->
            if(events.isEmpty())Text("Nenhum evento disponível. Confira se a agenda está sincronizada no Android.")
            events.forEach{event->
                val whenText=if(event.allDay)Instant.ofEpochMilli(event.begin).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd/MM"))+" · dia inteiro"
                    else Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                TextButton(onClick={openIntent(context,Intent(Intent.ACTION_VIEW,ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,event.id))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,event.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,event.end))}){Text("$whenText · ${event.title}")}
            }
            if(events.size==20)Text("Lista limitada a 20 ocorrências. Abra o calendário para ver mais.",fontSize=12.sp,color=KoiColors.Muted)
        }
    }
}
