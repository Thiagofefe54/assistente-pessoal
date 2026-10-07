package com.thiago.assistentepessoal

import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.chat.ChatBackend
import com.thiago.assistentepessoal.cloud.CloudApi
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URLEncoder
import java.util.UUID

/** Explicit opt-in. Creates and removes only its own fictional fixture. */
class MemoryLiveTest {
    @Test fun savedMemoryIsRecalledEditedAndDeletedWithoutChatHistory() = runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("koiLiveBackend")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KoiwaiApplication
        val owner=requireNotNull(app.auth.account.value) { "Entre na Koiwai antes do teste." }.id
        fun path(id: String)="/rest/v1/memory_facts?user_id=eq.$owner&id=eq.$id"
        suspend fun request(path:String, method:String="GET", body:String?=null)=
            CloudApi.request(path,method,body,app.auth.token(owner),"return=representation")
        val slots=JSONArray(request("/rest/v1/memory_facts?select=slot&user_id=eq.$owner&limit=20"))
        val used=(0 until slots.length()).map {slots.getJSONObject(it).getInt("slot")}
        val slot=(1..20).firstOrNull {it !in used}
        assumeTrue("Sem espaço para fixture de teste.",slot!=null)
        val id=UUID.randomUUID().toString()
        try {
            val inserted=JSONArray(request("/rest/v1/memory_facts","POST",JSONObject()
                .put("id",id).put("user_id",owner).put("slot",slot).put("category","note")
                .put("content","Lembrança fictícia de teste: meu projeto imaginário chama Farol de Jade.").toString()))
            assertEquals(1,inserted.length())
            val old=inserted.getJSONObject(0).getString("updated_at")
            val changed=JSONArray(request(path(id),"PATCH",JSONObject()
                .put("content","Lembrança fictícia de teste: meu projeto imaginário chama Farol de Rubi.").toString()))
            assertEquals(1,changed.length())
            val stale=JSONArray(request(path(id)+"&updated_at=eq."+URLEncoder.encode(old,"UTF-8"),"PATCH",
                JSONObject().put("content","Edição antiga inválida").toString()))
            assertEquals(0,stale.length())
            val backend=ChatBackend {app.auth.token(owner)}
            val result=backend.send("Qual é o nome do projeto imaginário da lembrança fictícia de teste? Responda só o nome.",emptyList())
            assertTrue("A memória corrigida deve ser recuperada sem histórico recente.",result.contains("Farol de Rubi",true))
            assertEquals(1,JSONArray(request(path(id),"DELETE")).length())
            assertEquals(0,JSONArray(request(path(id)+"&select=id")).length())
        } finally {request(path(id),"DELETE")}
    }
}
