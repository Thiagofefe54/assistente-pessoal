package com.thiago.assistentepessoal.chat

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

const val DEFAULT_CONVERSATION = "00000000-0000-0000-0000-000000000000"
const val NOTICES_CONVERSATION = "koi-notices"

@Entity(tableName="conversations")
data class ChatConversation(@PrimaryKey val id:String, val title:String="Nova conversa",val folder:String="Pessoal",val pinned:Boolean=false,val createdAt:Long=System.currentTimeMillis())

@Entity(tableName="notices")
data class KoiNotice(@PrimaryKey val id:String,val title:String,val content:String,val category:String,val occurredAt:Long=System.currentTimeMillis(),val read:Boolean=false)

object MessageStatus {
    const val SENDING = "sending"
    const val SENT = "sent"
    const val FAILED = "failed"
}

@Entity(tableName = "messages", indices = [Index(value = ["id"], unique = true),
    Index(value = ["replyTo"], unique = true)])
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val occurredAt: Long = Instant.now().toEpochMilli(),
    val timezone: String = ZoneId.systemDefault().id,
    val localDate: String = messageDate(occurredAt, timezone).toString(),
    val status: String = MessageStatus.SENT,
    val replyTo: String? = null,
    val error: String? = null,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    val taskDraftJson: String? = null,
    val actionReceiptJson: String? = null,
    val imageJpegBase64: String? = null,
    @ColumnInfo(defaultValue = "'00000000-0000-0000-0000-000000000000'") val conversationId:String = DEFAULT_CONVERSATION
)

@Dao
abstract class ChatDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sequence ASC")
    abstract fun observeConversation(conversationId:String):Flow<List<ChatMessage>>
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, createdAt DESC")
    abstract fun observeConversations():Flow<List<ChatConversation>>
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    abstract suspend fun createConversation(conversation:ChatConversation)
    @Query("UPDATE conversations SET title=:title, folder=:folder, pinned=:pinned WHERE id=:id")
    abstract suspend fun editConversation(id:String,title:String,folder:String,pinned:Boolean)
    @Query("UPDATE conversations SET title=:title WHERE id=:id AND title='Nova conversa'")
    abstract suspend fun nameConversation(id:String,title:String)
    @Query("SELECT * FROM notices ORDER BY occurredAt DESC")
    abstract fun observeNotices():Flow<List<KoiNotice>>
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    abstract suspend fun insertNotice(notice:KoiNotice)
    @Query("UPDATE notices SET read=1 WHERE id=:id")
    abstract suspend fun readNotice(id:String)
    @Query("UPDATE notices SET read=1")
    abstract suspend fun readAllNotices()
    @Query("DELETE FROM notices WHERE id IN (SELECT id FROM notices WHERE occurredAt < :cutoff ORDER BY occurredAt ASC LIMIT 50)")
    abstract suspend fun trimNotices(cutoff:Long):Int

    @Query("SELECT * FROM messages ORDER BY sequence ASC")
    abstract fun observeMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages ORDER BY sequence ASC")
    abstract suspend fun getMessages(): List<ChatMessage>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'sent' AND id != :currentId AND occurredAt <= :beforeTime ORDER BY occurredAt DESC, sequence DESC LIMIT 20")
    abstract suspend fun recentContext(currentId: String, beforeTime: Long, conversationId:String = DEFAULT_CONVERSATION): List<ChatMessage>

    @Insert
    abstract suspend fun insert(message: ChatMessage)

    @Query("SELECT * FROM messages WHERE id = :id")
    abstract suspend fun find(id: String): ChatMessage?

    @Query("UPDATE messages SET status = :status, error = :error WHERE id = :id")
    abstract suspend fun updateStatus(id: String, status: String, error: String?)

    @Query("UPDATE messages SET status = 'failed', error = 'Envio interrompido. Tente novamente.' WHERE status = 'sending'")
    abstract suspend fun recoverInterruptedSends()

    @Query("SELECT * FROM messages WHERE status = 'sent' AND synced = 0 ORDER BY sequence ASC")
    abstract suspend fun pendingSync(): List<ChatMessage>

    @Query("UPDATE messages SET synced = 1 WHERE id IN (:ids)")
    abstract suspend fun markSynced(ids: List<String>)

    @Query("UPDATE messages SET taskDraftJson = NULL WHERE id = :id")
    abstract suspend fun dismissTaskDraft(id: String)
    @Query("UPDATE messages SET actionReceiptJson = NULL WHERE id = :id")
    abstract suspend fun dismissActionReceipt(id:String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(message: ChatMessage)

    @Transaction
    open suspend fun merge(messages: List<ChatMessage>) {
        messages.forEach {
            createConversation(ChatConversation(it.conversationId, if(it.role=="user")it.content.take(60) else "Nova conversa",createdAt=it.occurredAt))
            insertIfMissing(it.copy(sequence = 0))
        }
    }

    @Transaction
    open suspend fun saveUndo(user:ChatMessage,reply:String,originalMessageId:String) {
        insert(user);complete(user,reply);dismissActionReceipt(originalMessageId)
    }

    @Transaction
    open suspend fun complete(user: ChatMessage, reply: String, taskDraftJson: String? = null, actionReceiptJson:String?=null) {
        insert(ChatMessage(role = "assistant", content = reply, replyTo = user.id, taskDraftJson=taskDraftJson,actionReceiptJson=actionReceiptJson,conversationId=user.conversationId))
        updateStatus(user.id, MessageStatus.SENT, null)
    }
}

@Database(entities = [ChatMessage::class,ChatConversation::class,KoiNotice::class], version = 6, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messages(): ChatDao

    companion object {
        val MIGRATION_5_6 = object:Migration(5,6){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE messages ADD COLUMN conversationId TEXT NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'")
                db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, folder TEXT NOT NULL, pinned INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS notices (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, content TEXT NOT NULL, category TEXT NOT NULL, occurredAt INTEGER NOT NULL, read INTEGER NOT NULL)")
                db.execSQL("INSERT INTO conversations SELECT '00000000-0000-0000-0000-000000000000','Conversa anterior','Pessoal',0,MIN(occurredAt) FROM messages HAVING COUNT(*)>0")
            }
        }
        val MIGRATION_4_5 = object:Migration(4,5) {
            override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE messages ADD COLUMN imageJpegBase64 TEXT")}
        }
        val MIGRATION_3_4 = object:Migration(3,4) {
            override fun migrate(db:SupportSQLiteDatabase) {db.execSQL("ALTER TABLE messages ADD COLUMN actionReceiptJson TEXT")}
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN taskDraftJson TEXT")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun open(context: Context, userId: String? = null): ChatDatabase = Room.databaseBuilder(
            context.applicationContext, ChatDatabase::class.java, if (userId == null) "koiwai-chat.db" else "koiwai-$userId.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }
}
