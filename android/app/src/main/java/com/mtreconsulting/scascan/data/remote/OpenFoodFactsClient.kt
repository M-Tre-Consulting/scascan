package com.mtreconsulting.scascan.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenFoodFactsClient @Inject constructor() {
    private val client = OkHttpClient()

    /**
     * Fetches product data from OpenFoodFacts for a given barcode.
     * Returns a JSON string of the product data or null if not found.
     */
    suspend fun getProduct(barcode: String): String? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ScaScan - Android - Version 1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("OFFClient", "Unsuccessful response for $barcode: ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optInt("status") == 1) {
                    // Extract only the product portion to keep the prompt size manageable
                    json.optJSONObject("product")?.toString()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("OFFClient", "Error fetching barcode $barcode", e)
            null
        }
    }
}
