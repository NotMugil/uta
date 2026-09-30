package com.notmugil.uta.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class StoredCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)

@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "server_credentials"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }

    fun save(credentials: StoredCredentials) {
        prefs.edit().apply {
            putString(KEY_SERVER_URL, credentials.serverUrl)
            putString(KEY_USERNAME, credentials.username)
            putString(KEY_PASSWORD, credentials.password)
            apply()
        }
    }

    fun load(): StoredCredentials? {
        return try {
            val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
            val username = prefs.getString(KEY_USERNAME, null) ?: return null
            val password = prefs.getString(KEY_PASSWORD, null) ?: return null
            StoredCredentials(
                serverUrl = serverUrl,
                username = username,
                password = password
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
