package com.thiago.assistentepessoal

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp

internal val cachedAssistantPaths=setOf("day","review","plan","search")
/** Snapshot only, private no-backup storage, isolated by signed-in owner. Never caches writes. */
internal class AssistantReadCache(context:Context,owner:String){
    private val dir=File(context.noBackupFilesDir,"assistant-read/"+UUID.fromString(owner).toString())
    private fun key(path:String,body:JSONObject):File {
        val bytes=MessageDigest.getInstance("SHA-256").digest((path+"|"+body.toString()).toByteArray(Charsets.UTF_8))
        return File(dir,bytes.joinToString(""){"%02x".format(it)}+".json")
    }
    @Synchronized fun save(path:String,body:JSONObject,data:JSONObject){
        if(path !in cachedAssistantPaths)return
        runCatching {
            dir.mkdirs();val file=key(path,body);val temp=File(dir,file.name+".tmp")
            temp.writeText(JSONObject().put("saved_at",System.currentTimeMillis()).put("data",data).toString())
            check(temp.renameTo(file))
            dir.listFiles()?.filter{it.extension=="json"}?.sortedByDescending{it.lastModified()}?.drop(20)?.forEach{it.delete()}
        }
    }
    @Synchronized fun read(path:String,body:JSONObject):JSONObject? {
        if(path !in cachedAssistantPaths)return null
        return runCatching {
            val file=key(path,body);if(!file.exists() || file.length()>120000)return null
            val saved=JSONObject(file.readText());val time=saved.getLong("saved_at")
            if(!snapshotIsUsable(time,System.currentTimeMillis())){file.delete();return null}
            saved.getJSONObject("data").put("_offline",true).put("_cached_at",time)
        }.getOrNull()
    }
    @Synchronized fun clear(){dir.listFiles()?.forEach{it.delete()}}
}
internal fun snapshotIsUsable(saved:Long,now:Long)=saved>0 && now>=saved && now-saved<=7L*24*60*60*1000
@Composable internal fun AssistantCacheNotice(data:JSONObject){
    if(data.optBoolean("_offline")){
        val instant=java.time.Instant.ofEpochMilli(data.getLong("_cached_at")).atZone(java.time.ZoneId.systemDefault())
        Text("Sem atualização online. Cópia de ${instant.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))}. Os dados podem ter mudado.",color=KoiColors.Red,fontSize=12.sp)
    }
}
