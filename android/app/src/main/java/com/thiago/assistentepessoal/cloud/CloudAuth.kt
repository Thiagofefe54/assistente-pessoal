package com.thiago.assistentepessoal.cloud

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class Account(val id: String, val email: String)

class CloudAuth(private val store: SessionStore) {
    private var session: JSONObject? = store.load()
    private val mutex = Mutex()
    private fun accountOf(s: JSONObject?): Account? = s?.getJSONObject("user")?.let {
        Account(it.getString("id"), it.optString("email"))
    }
    private val _account = MutableStateFlow(accountOf(session))
    val account: StateFlow<Account?> = _account.asStateFlow()
    private fun remember(s: JSONObject) {
        if (!s.has("expires_at")) s.put("expires_at", System.currentTimeMillis()/1000 + s.getLong("expires_in"))
        store.save(s)
        session = s
        _account.value = accountOf(s)
    }
    suspend fun login(email: String, password: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            remember(JSONObject(CloudApi.request("/auth/v1/token?grant_type=password", "POST",
                JSONObject().put("email", email.trim()).put("password", password).toString())))
        }
    }
    suspend fun signup(email: String, password: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val result = JSONObject(CloudApi.request("/auth/v1/signup", "POST",
                JSONObject().put("email", email.trim()).put("password", password).toString()))
            if (result.optString("access_token").isNotBlank()) { remember(result); true } else false
        }
    }
    suspend fun token(expectedUser: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            var current = session ?: throw CloudException(401)
            if (accountOf(current)?.id != expectedUser) throw CloudException(401)
            if (current.getLong("expires_at") <= System.currentTimeMillis()/1000 + 60) {
                current = JSONObject(CloudApi.request("/auth/v1/token?grant_type=refresh_token", "POST",
                    JSONObject().put("refresh_token", current.getString("refresh_token")).toString()))
                if (accountOf(current)?.id != expectedUser) throw CloudException(401)
                remember(current)
            }
            current.getString("access_token")
        }
    }
    suspend fun logout() = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                session?.let { CloudApi.request("/auth/v1/logout?scope=local", "POST", token=it.getString("access_token")) }
            } catch (e: Exception) { if (e is CancellationException) throw e }
            finally { store.clear(); session = null; _account.value = null }
        }
    }
}
