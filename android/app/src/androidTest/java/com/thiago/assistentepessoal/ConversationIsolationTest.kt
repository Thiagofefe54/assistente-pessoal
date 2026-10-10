package com.thiago.assistentepessoal

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.chat.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ConversationIsolationTest {
    @Test fun contextAndRepliesStayInsideTheirConversation()=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,ChatDatabase::class.java).build()
        try{
            val dao=db.messages()
            val a=ChatMessage(role="user",content="Teste estudo",conversationId="a")
            val b=ChatMessage(role="user",content="Teste trabalho",conversationId="b")
            dao.insert(a);dao.complete(a,"Resposta de estudo")
            dao.insert(b);dao.complete(b,"Resposta de trabalho")
            assertEquals(listOf("Teste estudo","Resposta de estudo"),dao.observeConversation("a").first().map{it.content})
            assertTrue(dao.recentContext("new",Long.MAX_VALUE,"a").all{it.conversationId=="a"})
            assertEquals(2,dao.recentContext("new",Long.MAX_VALUE,"b").size)
        }finally{db.close()}
    }
    @Test fun restoringMessagesRebuildsThreadsWithoutOverwritingLocalOrganization()=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,ChatDatabase::class.java).build()
        try{
            val dao=db.messages()
            dao.createConversation(ChatConversation("a","Meu assunto","Estudos",true))
            dao.merge(listOf(ChatMessage(role="user",content="Teste sincronizado",conversationId="a"),ChatMessage(role="user",content="Outro teste",conversationId="b")))
            val chats=dao.observeConversations().first()
            assertEquals(2,chats.size)
            assertEquals("Meu assunto",chats.first{it.id=="a"}.title)
            assertTrue(chats.first{it.id=="a"}.pinned)
            assertEquals("Estudos",chats.first{it.id=="a"}.folder)
            assertEquals(setOf("a","b"),dao.getMessages().map{it.conversationId}.toSet())
        }finally{db.close()}
    }
    @Test fun retentionRemovesOnlyExpiredNoticesInBatchesOfFifty()=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,ChatDatabase::class.java).build()
        try{
            val dao=db.messages()
            val now=System.currentTimeMillis();val cutoff=now-60L*86_400_000L
            repeat(61){dao.insertNotice(KoiNotice("old-$it","Teste","Aviso fictício","Teste",cutoff-1))}
            dao.insertNotice(KoiNotice("boundary","Teste","Limite","Teste",cutoff))
            dao.insertNotice(KoiNotice("new","Teste","Novo aviso","Teste",now))
            dao.insert(ChatMessage(role="user",content="Conversa preservada",occurredAt=cutoff-1))
            assertEquals(50,dao.trimNotices(cutoff));assertEquals(13,dao.observeNotices().first().size)
            assertEquals(11,dao.trimNotices(cutoff));assertEquals(2,dao.observeNotices().first().size)
            assertEquals(1,dao.getMessages().size)
        }finally{db.close()}
    }
    @Test fun readStateAndDuplicateNoticesAreStable()=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,ChatDatabase::class.java).build()
        try{
            val dao=db.messages();val notice=KoiNotice("id","Teste","Recado fictício","Teste")
            dao.insertNotice(notice);dao.readNotice("id");dao.insertNotice(notice)
            assertEquals(1,dao.observeNotices().first().size);assertTrue(dao.observeNotices().first().single().read)
            dao.insertNotice(notice.copy(id="other"));dao.readAllNotices()
            assertTrue(dao.observeNotices().first().all{it.read})
        }finally{db.close()}
    }
}
