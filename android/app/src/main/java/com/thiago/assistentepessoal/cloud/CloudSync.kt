package com.thiago.assistentepessoal.cloud

import android.content.Context
import androidx.work.*
import com.thiago.assistentepessoal.KoiwaiApplication
import com.thiago.assistentepessoal.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class CloudSync(private val context: Context, private val auth: CloudAuth,
                val userId: String, private val dao: ChatDao) {
    private val mutex = Mutex()
    suspend fun run() = mutex.withLock {
        withContext(Dispatchers.IO) {
            dao.pendingSync().chunked(100).forEach { batch ->
                val body = JSONArray()
                batch.forEach { m ->
                    body.put(JSONObject().put("user_id", userId).put("id", m.id)
                        .put("role", m.role).put("content", m.content)
                        .put("occurred_at", Instant.ofEpochMilli(m.occurredAt).toString())
                        .put("timezone", m.timezone).put("reply_to", m.replyTo ?: JSONObject.NULL))
                }
                CloudApi.request("/rest/v1/chat_messages?on_conflict=user_id,id", "POST", body.toString(),
                    auth.token(userId), "resolution=ignore-duplicates,return=minimal")
                dao.markSynced(batch.map { it.id })
            }
            // Reconcile from the beginning: identity sequences are not commit-order cursors.
            var cursor = 0L
            while (true) {
                val rows = JSONArray(CloudApi.request(
                    "/rest/v1/chat_messages?select=*&user_id=eq.$userId&server_sequence=gt.$cursor&order=server_sequence.asc&limit=100",
                    token = auth.token(userId)))
                if (rows.length() == 0) break
                val messages = (0 until rows.length()).map { i ->
                    val row = rows.getJSONObject(i)
                    ChatMessage(id=row.getString("id"), role=row.getString("role"), content=row.getString("content"),
                        occurredAt=Instant.parse(row.getString("occurred_at")).toEpochMilli(),
                        timezone=row.getString("timezone"), localDate=row.getString("local_date"),
                        replyTo=if (row.isNull("reply_to")) null else row.getString("reply_to"), synced=true)
                }
                dao.merge(messages)
                cursor = rows.getJSONObject(rows.length()-1).getLong("server_sequence")
            }
        }
    }
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<ChatSyncWorker>()
            .setInputData(workDataOf("userId" to userId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("chat-sync-$userId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

class ChatSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KoiwaiApplication
        val owner = inputData.getString("userId") ?: return Result.failure()
        if (app.auth.account.value?.id != owner) return Result.success()
        val sync = app.cloudSync ?: return Result.retry()
        if (sync.userId != owner) return Result.success()
        return try { sync.run(); Result.success() }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is CloudException && e.code in listOf(400,401,403,409,422)) Result.failure() else Result.retry()
        }
    }
}
