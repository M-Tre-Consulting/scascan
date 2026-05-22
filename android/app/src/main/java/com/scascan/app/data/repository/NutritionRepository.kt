package com.scascan.app.data.repository

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.data.model.NutritionFacts
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NutritionRepository @Inject constructor(
    private val keyStore: GeminiKeyStore,
    private val gson: Gson
) {
    private val responseSchema = """
        Return ONLY a JSON object with this exact structure, no markdown, no extra text:
        {"foodName":"string","servingSize":"string","calories":0,"protein":0,"carbohydrates":0,"fat":0,"fiber":0,"sugar":0,"sodium":0}
        Numeric values: protein/carbohydrates/fat/fiber/sugar in grams, sodium in milligrams, all per serving.
    """.trimIndent()

    private fun model(): GenerativeModel {
        val key = keyStore.apiKey
        require(key.isNotBlank()) { "Gemini API key is not configured. Please set it in the app settings." }
        return GenerativeModel(modelName = "gemini-2.0-flash", apiKey = key)
    }

    suspend fun analyzeImage(bitmap: Bitmap): Result<NutritionFacts> = runCatching {
        val inputContent = content {
            image(bitmap)
            text("You are a nutrition expert. Identify the food in this image and provide nutritional facts. $responseSchema")
        }
        parseResponse(model().generateContent(inputContent).text ?: error("Empty response from Gemini"))
    }

    suspend fun analyzeBarcode(barcode: String): Result<NutritionFacts> = runCatching {
        val prompt = "You are a nutrition expert. The barcode value is '$barcode'. Identify the food product and provide its nutritional facts. $responseSchema"
        parseResponse(model().generateContent(prompt).text ?: error("Empty response from Gemini"))
    }

    suspend fun searchFood(query: String): Result<NutritionFacts> = runCatching {
        val prompt = "You are a nutrition expert. Provide nutritional facts for: $query. $responseSchema"
        parseResponse(model().generateContent(prompt).text ?: error("Empty response from Gemini"))
    }

    private fun parseResponse(text: String): NutritionFacts {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start != -1 && end != -1) { "No JSON found in AI response" }
        return gson.fromJson(text.substring(start, end + 1), NutritionFacts::class.java)
    }
}
