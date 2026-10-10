package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import com.thiago.assistentepessoal.cloud.CloudApi
import java.util.UUID
import java.net.URLEncoder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in: existing owner session, read-only Google, no model generation. */
class IntegratedPackReadTest {
    @Test fun fictionalMemoryCanBeReadCorrectedAndRemovedWithoutModel()=runBlocking(Dispatchers.IO){
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiPackRead")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val owner=requireNotNull(app.auth.account.value).id
        suspend fun request(path:String,method:String="GET",body:JSONObject?=null)=JSONArray(
            CloudApi.request(path,method,body?.toString(),app.auth.token(owner),"return=representation"))
        val prefix="/rest/v1/memory_facts"
        val rows=request("$prefix?select=slot&user_id=eq.$owner&limit=20")
        val occupied=(0 until rows.length()).map{rows.getJSONObject(it).getInt("slot")}
        val slot=(1..20).firstOrNull{it !in occupied}
        assumeTrue("Sem espaço livre para uma lembrança fictícia.",slot!=null)
        val id=UUID.randomUUID().toString()
        val path="$prefix?id=eq.$id&user_id=eq.$owner"
        try{
            val created=request(prefix,"POST",JSONObject().put("id",id).put("user_id",owner).put("slot",slot)
                .put("category","note").put("content","Teste fictício: treino imaginário às 18h."))
            assertEquals(1,created.length())
            val original=created.getJSONObject(0).getString("updated_at")
            assertEquals("Teste fictício: treino imaginário às 18h.",request(path).getJSONObject(0).getString("content"))
            val version="&updated_at=eq."+URLEncoder.encode(original,"UTF-8")
            assertEquals(1,request(path+version,"PATCH",JSONObject().put("content","Teste fictício: treino imaginário às 19h.")).length())
            assertEquals(0,request(path+version,"PATCH",JSONObject().put("content","Edição antiga inválida")).length())
            assertEquals("Teste fictício: treino imaginário às 19h.",request(path).getJSONObject(0).getString("content"))
        }finally{
            request(path,"DELETE")
            assertEquals(0,request(path).length())
        }
    }
    @Test fun connectedAccountsAndSelectedListsUseTheInstalledClient()=runBlocking(Dispatchers.IO){
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiPackRead")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val owner=requireNotNull(app.auth.account.value){"Entre na Koi antes do teste."}.id
        val status=assistantRequest(app,owner,"google-status",allowCached=false)
        assertTrue(status.getBoolean("configured"))
        val accounts=status.getJSONArray("accounts")
        assertEquals(3,accounts.length())
        for(i in 0 until accounts.length()){
            val id=accounts.getJSONObject(i).getString("id")
            suspend fun read(service:String,selection:String?=null):JSONObject{
                val body=JSONObject().put("connection_id",id).put("service",service)
                selection?.let{body.put(if(service=="calendar")"calendar_id" else "list_id",it)}
                val result=assistantRequest(app,owner,"google-read",body,allowCached=false)
                assertEquals(id,result.getJSONObject("account").getString("id"))
                assertEquals(service,result.getString("service"))
                assertFalse(result.has("api_key"));assertFalse(result.has("access_token"))
                return result
            }
            assertTrue(read("calendar").getJSONArray("items").length()<=60)
            assertTrue(read("task_items").getJSONArray("items").length()<=180)
            for(service in listOf("calendars","tasks")){
                val choices=read(service).getJSONArray("items")
                if(choices.length()>0){
                    val selected=read(if(service=="calendars")"calendar" else "task_items",choices.getJSONObject(0).getString("id"))
                    assertTrue(selected.getJSONArray("items").length()<=60)
                }
            }
        }
        assertEquals(owner,app.auth.account.value?.id)
    }
}
