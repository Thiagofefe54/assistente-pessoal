package com.thiago.assistentepessoal

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.thiago.assistentepessoal.chat.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ChatPersistenceTest {
    @Test fun selectedImageSurvivesTextOnlyCloudReconciliation() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,ChatDatabase::class.java).build()
        try{
            val message=ChatMessage(role="user",content="Imagem fictícia",imageJpegBase64="fixture-base64")
            db.messages().insert(message)
            db.messages().merge(listOf(message.copy(sequence=0,synced=true,imageJpegBase64=null)))
            assertEquals("fixture-base64",db.messages().find(message.id)?.imageJpegBase64)
        }finally{db.close()}
    }
    @Test fun actionReceiptSurvivesSyncAndUndoKeepsConversation() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,ChatDatabase::class.java).build()
        try {
            val user=ChatMessage(role="user",content="Conclua a tarefa fictícia")
            val receipt="""{"request_id":"${UUID.randomUUID()}","type":"complete","task_id":"${UUID.randomUUID()}","title":"Fictícia"}"""
            db.messages().insert(user);db.messages().complete(user,"Concluída",actionReceiptJson=receipt)
            val original=db.messages().getMessages().last()
            db.messages().merge(listOf(original.copy(sequence=0,actionReceiptJson=null,synced=true)))
            assertEquals(receipt,db.messages().find(original.id)?.actionReceiptJson)
            db.messages().saveUndo(ChatMessage(role="user",content="Desfaça"),"Desfeito",original.id)
            assertNull(db.messages().find(original.id)?.actionReceiptJson)
            assertEquals(4,db.messages().getMessages().size)
        } finally {db.close()}
    }
    @Test fun proposalSurvivesReconciliationAndCanBeDismissedWithoutDeletingConversation() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,ChatDatabase::class.java).build()
        try {
            val user=ChatMessage(role="user",content="Crie tarefa fictícia")
            db.messages().insert(user)
            val draft="""{"title":"Tarefa fictícia","notes":"","due_date":null,"due_time":null,"recurrence":"none"}"""
            db.messages().complete(user,"Revise antes de salvar",draft)
            val assistant=db.messages().getMessages().last()
            db.messages().merge(listOf(assistant.copy(sequence=0,synced=true,taskDraftJson=null)))
            assertEquals(draft,db.messages().find(assistant.id)?.taskDraftJson)
            db.messages().dismissTaskDraft(assistant.id)
            assertNull(db.messages().find(assistant.id)?.taskDraftJson)
            assertEquals(2,db.messages().getMessages().size)
        } finally {db.close()}
    }
    @Test fun migrationKeepsTheExistingLocalConversation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        sqlite.execSQL("CREATE TABLE messages (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, id TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, occurredAt INTEGER NOT NULL, timezone TEXT NOT NULL, localDate TEXT NOT NULL, status TEXT NOT NULL, replyTo TEXT, error TEXT)")
        sqlite.execSQL("CREATE UNIQUE INDEX index_messages_id ON messages(id)")
        sqlite.execSQL("CREATE UNIQUE INDEX index_messages_replyTo ON messages(replyTo)")
        sqlite.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        sqlite.execSQL("INSERT INTO room_master_table VALUES(42, 'eb7de3738519177cacbeff9675f1a270')")
        sqlite.execSQL("INSERT INTO messages(id,role,content,occurredAt,timezone,localDate,status) VALUES('legacy','user','Conversa anterior',1791081000000,'America/Sao_Paulo','2026-10-03','sent')")
        sqlite.version = 1
        sqlite.close()
        val db = Room.databaseBuilder(context, ChatDatabase::class.java, name)
            .addMigrations(ChatDatabase.MIGRATION_1_2,ChatDatabase.MIGRATION_2_3,ChatDatabase.MIGRATION_3_4,ChatDatabase.MIGRATION_4_5).build()
        try {
            val saved = db.messages().getMessages().single()
            assertEquals("Conversa anterior", saved.content)
            assertEquals("legacy", saved.id)
            assertFalse(saved.synced)
            assertEquals(1, db.messages().pendingSync().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun cloudReconciliationIsIdempotent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        try {
            val user = ChatMessage(role="user", content="Fictícia")
            db.messages().insert(user)
            db.messages().complete(user, "Resposta fictícia")
            val messages = db.messages().getMessages()
            assertEquals(2, db.messages().pendingSync().size)
            db.messages().markSynced(messages.map { it.id })
            db.messages().merge(messages.map { it.copy(sequence=0, synced=true) })
            db.messages().merge(messages.map { it.copy(sequence=0, synced=true) })
            assertEquals(2, db.messages().getMessages().size)
            assertTrue(db.messages().pendingSync().isEmpty())
        } finally { db.close() }
    }

    @Test fun reopenRecoverAndRetryKeepOneConversation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "chat-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, ChatDatabase::class.java, name).build()
        var db = open()
        try {
            val timestamp = Instant.parse("2026-10-04T02:59:59Z").toEpochMilli()
            val user = ChatMessage(role = "user", content = "Teste \"aspas\"\nsegunda linha",
                occurredAt = timestamp, timezone = "America/Sao_Paulo", status = MessageStatus.SENDING)
            db.messages().insert(user)
            db.close()
            db = open()
            db.messages().recoverInterruptedSends()
            val saved = db.messages().getMessages().single()
            assertEquals(user.id, saved.id)
            assertEquals(user.content, saved.content)
            assertEquals("2026-10-03", saved.localDate)
            assertEquals(MessageStatus.FAILED, saved.status)
            db.messages().updateStatus(user.id, MessageStatus.SENDING, null)
            db.messages().complete(saved, "Resposta\ncom \"aspas\"")
            db.close()
            db = open()
            val history = db.messages().getMessages()
            assertEquals(2, history.size)
            assertEquals(MessageStatus.SENT, history[0].status)
            assertEquals(user.id, history[1].replyTo)
            assertEquals(timestamp, history[0].occurredAt)
            try {
                db.messages().complete(saved, "Duplicada")
                fail("A duplicate reply must be rejected")
            } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(2, db.messages().getMessages().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
