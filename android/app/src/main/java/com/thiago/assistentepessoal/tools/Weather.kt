package com.thiago.assistentepessoal.tools

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.thiago.assistentepessoal.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun weatherJson(url:String):JSONObject{
    val connection=URL(url).openConnection() as HttpURLConnection
    try{
        connection.connectTimeout=15000;connection.readTimeout=15000;connection.instanceFollowRedirects=false
        connection.setRequestProperty("User-Agent","Koiwai/1.0")
        require(connection.responseCode==200){"Não consegui consultar o clima agora."}
        return connection.inputStream.use{stream->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true){val size=stream.read(buffer);if(size<0)break;require(output.size()+size<=100000);output.write(buffer,0,size)}
            JSONObject(String(output.toByteArray(),Charsets.UTF_8))
        }
    }finally{connection.disconnect()}
}
@Composable
fun WeatherPanel(compact:Boolean=false){
    val context=LocalContext.current
    val prefs=context.getSharedPreferences("koiwai-weather",0)
    val scope=rememberCoroutineScope()
    var query by rememberSaveable{mutableStateOf("")}
    var places by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var saved by remember{mutableStateOf(prefs.getString("place",null)?.let{runCatching{JSONObject(it)}.getOrNull()})}
    var display by remember{mutableStateOf(prefs.getString("display",null))}
    var busy by remember{mutableStateOf(false)}
    var info by remember{mutableStateOf<String?>(null)}
    var choosing by remember{mutableStateOf(!compact || saved==null)}
    fun forecast(place:JSONObject){if(busy)return;busy=true;info=null;scope.launch{
        try{
            val response=withContext(Dispatchers.IO){weatherJson("https://api.open-meteo.com/v1/forecast?latitude=${place.getDouble("latitude")}&longitude=${place.getDouble("longitude")}&current=temperature_2m,apparent_temperature,precipitation&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=1&timezone=auto")}
            val current=response.getJSONObject("current");val daily=response.getJSONObject("daily")
            display="${place.getString("name")} • ${current.getDouble("temperature_2m")} °C\nSensação ${current.getDouble("apparent_temperature")} °C\nHoje: ${daily.getJSONArray("temperature_2m_min").getDouble(0)} a ${daily.getJSONArray("temperature_2m_max").getDouble(0)} °C\nChance de chuva: ${daily.getJSONArray("precipitation_probability_max").getInt(0)}%\nDados: ${current.getString("time")} (${response.getString("timezone")})"
            saved=place;places=emptyList();if(compact)choosing=false;prefs.edit().putString("place",place.toString()).putString("display",display).putLong("updated",System.currentTimeMillis()).apply()
        }catch(e:Exception){if(e is CancellationException)throw e;info="Não consegui atualizar o clima. Dados anteriores podem estar desatualizados."}finally{busy=false}
    }}
    LaunchedEffect(Unit){saved?.let{if(System.currentTimeMillis()-prefs.getLong("updated",0)>3600000)forecast(it)}}
    KoiPanel(Modifier.fillMaxWidth(),accent=KoiColors.Blue){
        Text("Clima na sua cidade",fontSize=22.sp)
        display?.let{Text(it,color=KoiColors.Blue)}
        if(compact && saved!=null)TextButton(onClick={choosing=!choosing},enabled=!busy){Text(if(choosing)"Fechar busca" else "Mudar cidade")}
        if(choosing){
        OutlinedTextField(query,{if(it.length<=80)query=it},label={Text("Cidade")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
        TextButton(onClick={busy=true;info=null;scope.launch{try{
            val response=withContext(Dispatchers.IO){weatherJson("https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(query.trim())}&count=5&language=pt&format=json")}
            val rows=response.optJSONArray("results");places=if(rows==null)emptyList()else(0 until rows.length()).map{rows.getJSONObject(it)}
            if(places.isEmpty())info="Não encontrei essa cidade. Tente o nome completo."
        }catch(e:Exception){if(e is CancellationException)throw e;info="Não consegui pesquisar a cidade."}finally{busy=false}}},enabled=!busy && query.trim().length>=2){Text("Encontrar cidade")}
        places.forEach{place->TextButton(onClick={forecast(place)},enabled=!busy){Text(listOf(place.getString("name"),place.optString("admin1"),place.optString("country")).filter{it.isNotBlank()}.joinToString(" • "))}}
        }
        saved?.let{place->TextButton(onClick={forecast(place)},enabled=!busy){Text("Atualizar clima")}}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        info?.let{Text(it,color=KoiColors.Red)}
        Text("Fonte: Open-Meteo. Você escolhe a cidade; não usamos GPS. Previsão pode mudar.",fontSize=11.sp,color=KoiColors.Muted)
    }
}
