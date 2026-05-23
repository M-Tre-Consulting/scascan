package com.scascan.app.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.scascan.app.data.local.GeminiKeyStore
import com.scascan.app.data.local.UserProfileStore
import com.scascan.app.data.model.NutritionFacts
import com.scascan.app.data.model.NutritionTargets
import com.scascan.app.data.remote.GeminiRestClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NutritionRepository @Inject constructor(
    private val keyStore: GeminiKeyStore,
    private val client: GeminiRestClient,
    private val offClient: com.scascan.app.data.remote.OpenFoodFactsClient,
    private val gson: Gson
) {
    private val responseSchema = """
        Return ONLY a JSON object with this exact structure, no markdown, no extra text:
        {"foodName":"string","servingSize":"string","calories":0,"protein":0,"carbohydrates":0,"fat":0,"fiber":0,"sugar":0,"sodium":0}
        Numeric values: protein/carbohydrates/fat/fiber/sugar in grams, sodium in milligrams, all per serving.
    """.trimIndent()

    private fun apiKey(): String {
        val key = keyStore.apiKey
        require(key.isNotBlank()) { "Gemini API key is not configured — go to Profile to add it." }
        return key
    }

    private fun model(): String {
        val m = keyStore.selectedModel
        require(m.isNotBlank()) { "No AI model selected — go to Profile to choose one." }
        return m
    }

    suspend fun analyzeImage(bitmap: Bitmap): Result<NutritionFacts> = runCatching {
        parseResponse(
            client.generateWithImage(
                model(), apiKey(), bitmap,
                "You are a nutrition expert. Identify the food in this image and provide nutritional facts. $responseSchema"
            )
        )
    }

    suspend fun analyzeBarcodeImage(bitmap: Bitmap): Result<NutritionFacts> = runCatching {
        // 1. Ask Gemini to read the barcode number (OCR) from the image
        val barcodeOcrResult = client.generateWithImage(
            model(), apiKey(), bitmap,
            "You are a barcode scanner. Identify the numeric barcode value in this image. " +
            "Return ONLY the numbers, no text. If no barcode is visible, return 'NONE'."
        ).trim()

        val barcodeNumber = barcodeOcrResult.filter { it.isDigit() }

        if (barcodeNumber.isNotEmpty() && barcodeOcrResult != "NONE") {
            // 2. We have a barcode number, use the factual OpenFoodFacts database
            Log.d("NutritionRepo", "Gemini OCR found barcode: $barcodeNumber. Fetching factual data...")
            val offData = offClient.getProduct(barcodeNumber)
            
            if (offData != null) {
                Log.d("NutritionRepo", "Factual data found in OFF for $barcodeNumber")
                return@runCatching parseResponse(
                    client.generateText(
                        model(), apiKey(),
                        "You are a nutrition expert. I have fetched this raw factual data from OpenFoodFacts for barcode '$barcodeNumber':\n" +
                        "$offData\n\n" +
                        "Please parse this data and return it in the required JSON format. " +
                        "This product is specifically identified by its barcode, so ensure the food name (e.g., 'Coke Zero' vs 'Coke Original') is exact. " +
                        responseSchema
                    )
                )
            }
        }

        // 3. Fallback: If OCR failed or OFF has no data, use Gemini's vision to identify the product
        Log.d("NutritionRepo", "No factual barcode data found. Falling back to Gemini Vision identification.")
        parseResponse(
            client.generateWithImage(
                model(), apiKey(), bitmap,
                "You are a nutrition expert. Identify the product in this image (look at the label and any barcode). " +
                "Be very specific about the variety (e.g., 'Zero Sugar', 'Original', 'Light'). " +
                "Provide its nutritional facts in the required format. " +
                responseSchema
            )
        )
    }

    suspend fun analyzeBarcode(barcode: String): Result<NutritionFacts> = runCatching {
        Log.d("NutritionRepo", "Analyzing barcode: $barcode")
        val offData = offClient.getProduct(barcode)
        Log.d("NutritionRepo", "OpenFoodFacts data found: ${offData != null}")
        
        val prompt = if (offData != null) {
            "You are a nutrition expert. I have fetched this raw data from OpenFoodFacts for barcode '$barcode':\n" +
            "$offData\n\n" +
            "Please parse this data and return it in the required JSON format. " +
            "Ensure the food name is accurate and the nutritional values are per serving as indicated in the data. " +
            responseSchema
        } else {
            Log.d("NutritionRepo", "Falling back to pure AI lookup for $barcode")
            "You are a nutrition expert. The barcode is '$barcode'. " +
            "First, try to identify this product using your general knowledge of global barcodes (e.g., EAN-13, UPC). " +
            "If it's a specific product, provide its exact nutritional facts. " +
            "If you are not 100% sure, provide facts for the most likely generic version. " +
            responseSchema
        }

        parseResponse(client.generateText(model(), apiKey(), prompt))
    }

    suspend fun searchFood(query: String): Result<NutritionFacts> = runCatching {
        parseResponse(
            client.generateText(
                model(), apiKey(),
                "You are a nutrition expert. Provide nutritional facts for: $query. $responseSchema"
            )
        )
    }

    suspend fun computeTargets(profile: UserProfileStore): Result<NutritionTargets> = runCatching {
        val activityLabels = listOf("sedentary", "lightly active", "moderately active", "very active", "extra active")
        val goalLabels = listOf("lose weight (moderate calorie deficit)", "maintain current weight", "build muscle (moderate calorie surplus)")
        val activityLabel = activityLabels[profile.activityIndex.coerceIn(0, 4)]
        val goalLabel = goalLabels[profile.goalIndex.coerceIn(0, 2)]
        val sex = if (profile.isMale) "male" else "female"
        val prompt = """
            You are a certified nutritionist. Compute daily nutrition targets for this user:
            Age: ${profile.age}, Sex: $sex, Height: ${profile.heightCm}cm, Weight: ${profile.weightKg}kg
            Activity: $activityLabel, Goal: $goalLabel
            Return ONLY valid JSON with no markdown or extra text:
            {"dailyCalories":0,"proteinGrams":0,"carbsGrams":0,"fatGrams":0}
        """.trimIndent()
        val json = client.generateText(model(), apiKey(), prompt)
        val start = json.indexOf('{')
        val end = json.lastIndexOf('}')
        require(start != -1 && end != -1) { "No JSON in AI response" }
        gson.fromJson(json.substring(start, end + 1), NutritionTargets::class.java)
    }

    suspend fun fixEntry(foodName: String, servingSize: String, correction: String): Result<NutritionFacts> = runCatching {
        parseResponse(
            client.generateText(
                model(), apiKey(),
                "You are a nutrition expert. A food was originally identified as '$foodName' ($servingSize). " +
                "The user says the correct food is: $correction. " +
                "Provide updated nutritional facts for the correct food, keeping the same serving size if possible. " +
                responseSchema
            )
        )
    }

    private fun parseResponse(text: String): NutritionFacts {
        val start = text.indexOf('{')
        val end   = text.lastIndexOf('}')
        require(start != -1 && end != -1) { "No JSON found in AI response" }
        return gson.fromJson(text.substring(start, end + 1), NutritionFacts::class.java)
    }
}
