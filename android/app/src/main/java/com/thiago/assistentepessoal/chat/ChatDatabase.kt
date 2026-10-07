package com.thiago.assistentepessoal.chat

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

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
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false
)

@Dao
abstract class ChatDao {
    @Query("SELECT * FROM messages ORDER BY sequence ASC")
    abstract fun observeMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages ORDER BY sequence ASC")
    abstract suspend fun getMessages(): List<ChatMessage>

    @Query("SELECT * FROM messages WHERE status = 'sent' AND id != :currentId AND occurredAt <= :beforeTime ORDER BY occurredAt DESC, sequence DESC LIMIT 20")
    abstract suspend fun recentContext(currentId: String, beforeTime: Long): List<ChatMessage>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(message: ChatMessage)

    @Transaction
    open suspend fun merge(messages: List<ChatMessage>) {
        messages.forEach { insertIfMissing(it.copy(sequence = 0)) }
    }

    @Transaction
    open suspend fun complete(user: ChatMessage, reply: String) {
        insert(ChatMessage(role = "assistant", content = reply, replyTo = user.id))
        updateStatus(user.id, MessageStatus.SENT, null)
    }
}

@Database(entities = [ChatMessage::class], version = 2, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messages(): ChatDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun open(context: Context, userId: String? = null): ChatDatabase = Room.databaseBuilder(
            context.applicationContext, ChatDatabase::class.java, if (userId == null) "koiwai-chat.db" else "koiwai-$userId.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
