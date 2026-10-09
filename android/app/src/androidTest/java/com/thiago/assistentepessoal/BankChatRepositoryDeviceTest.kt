package com.thiago.assistentepessoal

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

/** Isolated in-memory history; no bank requests, real history writes or AI calls. */
class BankChatRepositoryDeviceTest {
    @Test fun bankQuestionBypassesAiAndStoresOnlyReadDescriptor()=runBlocking(Dispatchers.IO){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,ChatDatabase::class.java).build()
        val repo=ChatRepository(db,tokenProvider={error("Bank question must not call AI backend")})
        try {
            withTimeout(15000){
                repo.busy.first{!it};repo.messages.first{it!=null}
                val id=withContext(Dispatchers.Main){repo.send("Quanto eu tenho no meu banco?")}
                assertNotNull(id)
                val rows=repo.messages.first{it?.any{m->m.role=="assistant"}==true}!!
                val answer=rows.single{it.role=="assistant"}
                assertEquals(id,answer.replyTo)
                assertFalse(answer.content.contains("R$"))
                val descriptor=JSONObject(requireNotNull(answer.actionReceiptJson))
                assertEquals("bank",descriptor.getString("tool"))
                assertEquals("balance",descriptor.getString("query"))
                assertEquals(setOf("tool","type","query"),descriptor.keys().asSequence().toSet())
                assertTrue(rows.all{it.status==MessageStatus.SENT})
                assertTrue(recentChatContext(rows,ChatMessage(role="user",content="Oi")).none{it.content.contains("R$")})
            }
        } finally {db.close()}
    }
}
