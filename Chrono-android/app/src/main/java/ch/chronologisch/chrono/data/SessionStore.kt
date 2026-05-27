package ch.chronologisch.chrono.data

import android.content.Context

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("chrono_session", Context.MODE_PRIVATE)

    fun getToken(): String? = preferences.getString(KEY_TOKEN, null)

    fun saveToken(token: String) {
        preferences.edit().putString(KEY_TOKEN, token).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
    }
}
