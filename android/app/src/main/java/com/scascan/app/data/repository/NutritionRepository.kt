package com.scascan.app.data.repository

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.scascan.app.data.model.NutritionFacts
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NutritionRepository @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val gson: Gson
) {
    private val responseSchema = """
        Return ONLY a JSON object with this exact structure, no markdown, no extra text:
        {"foodName":"string","servingSize":"string","calories":0,"protein":0,"carbohydrates":0,"fat":0,"fiber":0,"sugar":0,"sodium":0}
        Numeric values: protein/carbohydrates/fat/fiber/sugar in grams, sodium in milligrams, all per serving.
    """.trimIndent()

    suspend fun analyzeImage(bitmap: Bitmap): Result<NutritionFacts> = runCatching {
        val inputContent = content {
            image(bitmap)
            text("You are a nutrition expert. Identify the food in this image and provide nutritional facts. $responseSchema")
        }
        val response = generativeModel.generateContent(inputContent)
        parseResponse(response.text ?: error("Empty response from Gemini"))
    }

    suspend fun analyzeBarcode(barcode: String): Result<NutritionFacts> = runCatching {
        val prompt = "You are a nutrition expert. The barcode value is '$barcode'. Identify the food product and provide its nutritional facts. $responseSchema"
        val response = generativeModel.generateContent(prompt)
        parseResponse(response.text ?: error("Empty response from Gemini"))
    }

    suspend fun searchFood(query: String): Result<NutritionFacts> = runCatching {
        val prompt = "You are a nutrition expert. Provide nutritional facts for: $query. $responseSchema"
        val response = generativeModel.generateContent(prompt)
        parseResponse(response.text ?: error("Empty response from Gemini"))
    }

    private fun parseResponse(text: String): NutritionFacts {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start != -1 && end != -1) { "No JSON found in AI response" }
        return gson.fromJson(text.substring(start, end + 1), NutritionFacts::class.java)
    }
}
