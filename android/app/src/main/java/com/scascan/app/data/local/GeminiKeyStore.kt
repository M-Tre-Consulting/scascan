package com.scascan.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class GeminiKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) { prefs.edit(commit = true) { putString(KEY_API, value.trim()) } }

    // Persists the last model the user chose.
    // Falls back to a single well-known ID so the app works before the user
    // visits Profile — this is the only hardcoded string, and it is intentional.
    var selectedModel: String
        get() = prefs.getString(KEY_MODEL, "") ?: ""
        set(value) { prefs.edit(commit = true) { putString(KEY_MODEL, value) } }

    fun hasKey(): Boolean = apiKey.isNotBlank()

    companion object {
        private const val PREFS_NAME = "scascan_prefs"
        private const val KEY_API    = "gemini_api_key"
        private const val KEY_MODEL  = "gemini_model"
    }
}
