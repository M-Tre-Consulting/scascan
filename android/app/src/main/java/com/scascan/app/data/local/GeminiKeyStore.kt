package com.scascan.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_GEMINI_API, "") ?: ""
        set(value) { prefs.edit().putString(KEY_GEMINI_API, value.trim()).apply() }

    fun hasKey(): Boolean = apiKey.isNotBlank()

    fun clearKey() { prefs.edit().remove(KEY_GEMINI_API).apply() }

    companion object {
        private const val PREFS_NAME = "scascan_prefs"
        private const val KEY_GEMINI_API = "gemini_api_key"
    }
}
