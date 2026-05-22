package com.scascan.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) { prefs.edit().putString(KEY_API, value.trim()).commit() }

    // Persists the last model the user chose.
    // Falls back to a single well-known ID so the app works before the user
    // visits Profile — this is the only hardcoded string and it is intentional.
    var selectedModel: String
        get() = prefs.getString(KEY_MODEL, FALLBACK_MODEL) ?: FALLBACK_MODEL
        set(value) { prefs.edit().putString(KEY_MODEL, value).commit() }

    fun hasKey(): Boolean = apiKey.isNotBlank()

    fun clearKey() { prefs.edit().remove(KEY_API).commit() }

    companion object {
        private const val PREFS_NAME = "scascan_prefs"
        private const val KEY_API    = "gemini_api_key"
        private const val KEY_MODEL  = "gemini_model"
        // Single fallback — updated to whatever is current; the real list is fetched at runtime.
        const val FALLBACK_MODEL = "gemini-2.5-flash-preview-05-20"
    }
}
