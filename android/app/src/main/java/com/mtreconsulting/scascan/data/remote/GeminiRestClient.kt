package com.mtreconsulting.scascan.data.remote

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

data class ModelInfo(val id: String, val displayName: String)

@Singleton
class GeminiRestClient @Inject constructor() {

    companion object {
        private const val TAG      = "GeminiRestClient"
        private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS   = 60_000
        private const val IMAGE_MAX_SIDE    = 1_024
        private const val IMAGE_QUALITY     = 85
    }

    /** Fetches every model available to [apiKey] that supports generateContent. */
    suspend fun listModels(apiKey: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val conn = (URL("$API_BASE?key=$apiKey").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout   = READ_TIMEOUT_MS
        }
        try {
            val code = conn.responseCode
            Log.d(TAG, "listModels HTTP $code")
            if (code != HttpURLConnection.HTTP_OK) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                Log.e(TAG, "listModels error: $err")
                throw IOException("Models list error $code: $err")
            }
            val json = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "listModels response (first 300): ${json.take(300)}")
            parseModelList(json)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseModelList(json: String): List<ModelInfo> {
        val result = mutableListOf<ModelInfo>()
        val arr = JSONObject(json).getJSONArray("models")
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            // Keep only models that support generateContent (excludes embedding-only models)
            val methods = obj.optJSONArray("supportedGenerationMethods") ?: continue
            val supported = (0 until methods.length()).any { methods.getString(it) == "generateContent" }
            if (!supported) continue
            val name = obj.getString("name") // e.g. "models/gemini-2.5-flash-preview-05-20"
            val id = name.removePrefix("models/")
            if (!id.startsWith("gemini-")) continue        // exclude AQA, PaLM 2, embedding-001, etc.
            val inputTokenLimit = obj.optInt("inputTokenLimit", 0)
            if (inputTokenLimit in 1..8_191) continue      // exclude Nano and other tiny-context models
            val label = obj.optString("displayName", id)
            result += ModelInfo(id, label)
        }
        Log.d(TAG, "listModels: ${result.size} usable models")
        return result
    }

    suspend fun generateText(model: String, apiKey: String, prompt: String): String {
        Log.d(TAG, "generateText() model=$model")
        return postWithRetry(model, apiKey, textBody(prompt))
    }

    suspend fun generateWithImage(
        model: String,
        apiKey: String,
        bitmap: Bitmap,
        prompt: String
    ): String {
        Log.d(TAG, "generateWithImage() model=$model bitmap=${bitmap.width}x${bitmap.height}")
        return postWithRetry(model, apiKey, imageBody(bitmap, prompt))
    }

    private suspend fun postWithRetry(model: String, apiKey: String, body: String): String {
        var retryCount = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (retryCount <= maxRetries) {
            try {
                return post(model, apiKey, body)
            } catch (e: IOException) {
                lastException = e
                val message = e.message ?: ""
                val isRetryable = message.contains("503") || message.contains("429") || 
                                 e is java.net.ConnectException || e is java.net.SocketTimeoutException

                if (isRetryable && retryCount < maxRetries) {
                    retryCount++
                    val delayMs = (2000L * 2.0.pow((retryCount - 1).toDouble())).toLong()
                    Log.w(TAG, "Retryable error: $message. Retrying in $delayMs ms... ($retryCount/$maxRetries)")
                    kotlinx.coroutines.delay(delayMs.milliseconds)
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: IOException("Failed after retries")
    }

    private suspend fun post(model: String, apiKey: String, body: String): String =
        withContext(Dispatchers.IO) {
            val endpoint = "$API_BASE/$model:generateContent"
            // Log URL with key redacted so we can verify the path without exposing secrets
            Log.d(TAG, "POST $endpoint?key=[REDACTED]")
            Log.d(TAG, "Request body (first 500 chars): ${body.take(500)}")

            val conn = (URL("$endpoint?key=$apiKey").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }
            try {
                conn.outputStream.bufferedWriter().use { it.write(body) }

                val code = conn.responseCode
                Log.d(TAG, "HTTP response code: $code")

                if (code != HttpURLConnection.HTTP_OK) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "(empty error body)"
                    Log.e(TAG, "Error body: $err")
                    throw IOException("Gemini API error $code — $err")
                }

                val response = conn.inputStream.bufferedReader().readText()
                Log.d(TAG, "Success body (first 500 chars): ${response.take(500)}")
                extractText(response)
            } finally {
                conn.disconnect()
            }
        }

    // Request builders
    private fun textBody(prompt: String): String =
        JSONObject()
            .put("contents", JSONArray()
                .put(JSONObject()
                    .put("parts", JSONArray()
                        .put(JSONObject().put("text", prompt)))))
            .toString()

    private fun imageBody(bitmap: Bitmap, prompt: String): String {
        val scaled = scaleBitmap(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, out)
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        Log.d(TAG, "Image encoded: ${scaled.width}x${scaled.height}, base64 length=${base64.length}")

        return JSONObject()
            .put("contents", JSONArray()
                .put(JSONObject()
                    .put("parts", JSONArray()
                        .put(JSONObject()
                            .put("inlineData", JSONObject()
                                .put("mimeType", "image/jpeg")
                                .put("data", base64)))
                        .put(JSONObject().put("text", prompt)))))
            .toString()
    }

    private fun scaleBitmap(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        if (w <= IMAGE_MAX_SIDE && h <= IMAGE_MAX_SIDE) return src
        val scale = IMAGE_MAX_SIDE.toFloat() / maxOf(w, h)
        return src.scale((w * scale).toInt(), (h * scale).toInt())
    }

    // Response parser
    private fun extractText(json: String): String {
        return try {
            JSONObject(json)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: $e\nRaw JSON: ${json.take(500)}")
            throw IOException("Unexpected Gemini response format: ${json.take(300)}")
        }
    }
}
