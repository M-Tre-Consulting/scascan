import Foundation
import UIKit

public enum NutritionRepositoryError: Error, LocalizedError, Sendable {
    case missingApiKey
    case missingModel
    case noJSONInResponse
    case malformedJSON

    public var errorDescription: String? {
        switch self {
        case .missingApiKey:
            return "Gemini API key is not configured — go to Profile to add it."
        case .missingModel:
            return "No AI model selected — go to Profile to choose one."
        case .noJSONInResponse:
            return "The AI response did not contain a valid JSON object. Please try again."
        case .malformedJSON:
            return "The AI returned malformed nutritional data. Refine your prompt and try again."
        }
    }
}

/// Mirrors Android's `data.repository.NutritionRepository` — same prompts,
/// same barcode OCR → OpenFoodFacts → vision-fallback pipeline, same JSON schema.
public final class NutritionRepository: Sendable {
    private let keyStore: GeminiKeyStore
    private let client: GeminiRestClient
    private let offClient: OpenFoodFactsClient
    private let decoder = JSONDecoder()

    private static let responseSchema = """
    Return ONLY a JSON object with this exact structure, no markdown, no extra text:
    {"foodName":"string","servingSize":"string","calories":0,"protein":0,"carbohydrates":0,"fat":0,"fiber":0,"sugar":0,"sodium":0}
    Numeric values: protein/carbohydrates/fat/fiber/sugar in grams, sodium in milligrams, all per serving.
    """

    public init(
        keyStore: GeminiKeyStore = .shared,
        client: GeminiRestClient = .shared,
        offClient: OpenFoodFactsClient = .shared
    ) {
        self.keyStore = keyStore
        self.client = client
        self.offClient = offClient
    }

    private func apiKey() throws -> String {
        let key = keyStore.apiKey
        guard !key.isEmpty else { throw NutritionRepositoryError.missingApiKey }
        return key
    }

    private func model() throws -> String {
        let m = keyStore.selectedModel
        guard !m.isEmpty else { throw NutritionRepositoryError.missingModel }
        return m
    }

    public func analyzeImage(_ image: UIImage) async throws -> NutritionFacts {
        let text = try await client.generateWithImage(
            model: model(),
            apiKey: apiKey(),
            image: image,
            prompt: "You are a nutrition expert. Identify the food in this image and provide nutritional facts. \(Self.responseSchema)"
        )
        return try Self.parseResponse(text, decoder: decoder)
    }

    public func analyzeBarcodeImage(_ image: UIImage) async throws -> NutritionFacts {
        let apiKey = try apiKey()
        let model = try model()

        // 1. Ask Gemini to read the barcode number (OCR) from the image.
        let ocrRaw = try await client.generateWithImage(
            model: model, apiKey: apiKey, image: image,
            prompt: "You are a barcode scanner. Identify the numeric barcode value in this image. " +
                "Return ONLY the numbers, no text. If no barcode is visible, return 'NONE'."
        ).trimmingCharacters(in: .whitespacesAndNewlines)
        let barcodeNumber = ocrRaw.filter(\.isNumber)

        if !barcodeNumber.isEmpty, ocrRaw != "NONE",
           let offData = await offClient.getProduct(barcode: barcodeNumber) {
            // 2. We have a barcode number — use the factual OpenFoodFacts database.
            let text = try await client.generateText(
                model: model, apiKey: apiKey,
                prompt: "You are a nutrition expert. I have fetched this raw factual data from OpenFoodFacts for barcode '\(barcodeNumber)':\n" +
                    "\(offData)\n\n" +
                    "Please parse this data and return it in the required JSON format. " +
                    "This product is specifically identified by its barcode, so ensure the food name (e.g., 'Coke Zero' vs 'Coke Original') is exact. " +
                    Self.responseSchema
            )
            return try Self.parseResponse(text, decoder: decoder)
        }

        // 3. Fallback: OCR failed or OFF has no data — use Gemini's vision to identify the product.
        let text = try await client.generateWithImage(
            model: model, apiKey: apiKey, image: image,
            prompt: "You are a nutrition expert. Identify the product in this image (look at the label and any barcode). " +
                "Be very specific about the variety (e.g., 'Zero Sugar', 'Original', 'Light'). " +
                "Provide its nutritional facts in the required format. " + Self.responseSchema
        )
        return try Self.parseResponse(text, decoder: decoder)
    }

    public func analyzeBarcode(_ barcode: String) async throws -> NutritionFacts {
        let offData = await offClient.getProduct(barcode: barcode)
        let prompt: String
        if let offData {
            prompt = "You are a nutrition expert. I have fetched this raw data from OpenFoodFacts for barcode '\(barcode)':\n" +
                "\(offData)\n\n" +
                "Please parse this data and return it in the required JSON format. " +
                "Ensure the food name is accurate and the nutritional values are per serving as indicated in the data. " +
                Self.responseSchema
        } else {
            prompt = "You are a nutrition expert. The barcode is '\(barcode)'. " +
                "First, try to identify this product using your general knowledge of global barcodes (e.g., EAN-13, UPC). " +
                "If it's a specific product, provide its exact nutritional facts. " +
                "If you are not 100% sure, provide facts for the most likely generic version. " + Self.responseSchema
        }
        let text = try await client.generateText(model: model(), apiKey: apiKey(), prompt: prompt)
        return try Self.parseResponse(text, decoder: decoder)
    }

    public func searchFood(_ query: String) async throws -> NutritionFacts {
        let text = try await client.generateText(
            model: model(), apiKey: apiKey(),
            prompt: "You are a nutrition expert. Provide nutritional facts for: \(query). \(Self.responseSchema)"
        )
        return try Self.parseResponse(text, decoder: decoder)
    }

    public func computeTargets(profile: UserProfileStore) async throws -> NutritionTargets {
        let activityLabels = ["sedentary", "lightly active", "moderately active", "very active", "extra active"]
        let goalLabels = [
            "lose weight (moderate calorie deficit)",
            "maintain current weight",
            "build muscle (moderate calorie surplus)"
        ]
        let activityLabel = activityLabels[min(max(profile.activityIndex, 0), 4)]
        let goalLabel = goalLabels[min(max(profile.goalIndex, 0), 2)]
        let sex = profile.isMale ? "male" : "female"
        let prompt = """
        You are a certified nutritionist. Compute daily nutrition targets for this user:
        Age: \(profile.age), Sex: \(sex), Height: \(profile.heightCm)cm, Weight: \(profile.weightKg)kg
        Activity: \(activityLabel), Goal: \(goalLabel)
        Return ONLY valid JSON with no markdown or extra text:
        {"dailyCalories":0,"proteinGrams":0,"carbsGrams":0,"fatGrams":0}
        """
        let json = try await client.generateText(model: model(), apiKey: apiKey(), prompt: prompt)
        guard let start = json.firstIndex(of: "{"), let end = json.lastIndex(of: "}") else {
            throw NutritionRepositoryError.noJSONInResponse
        }
        let slice = Data(json[start...end].utf8)
        return try decoder.decode(NutritionTargets.self, from: slice)
    }

    public func fixEntry(foodName: String, servingSize: String, correction: String) async throws -> NutritionFacts {
        let text = try await client.generateText(
            model: model(), apiKey: apiKey(),
            prompt: "You are a nutrition expert. A food was originally identified as '\(foodName)' (\(servingSize)). " +
                "The user says the correct food is: \(correction). " +
                "Provide updated nutritional facts for the correct food, keeping the same serving size if possible. " +
                Self.responseSchema
        )
        return try Self.parseResponse(text, decoder: decoder)
    }

    private static func parseResponse(_ text: String, decoder: JSONDecoder) throws -> NutritionFacts {
        guard let start = text.firstIndex(of: "{"), let end = text.lastIndex(of: "}") else {
            throw NutritionRepositoryError.noJSONInResponse
        }
        let slice = Data(text[start...end].utf8)
        do {
            return try decoder.decode(NutritionFacts.self, from: slice)
        } catch {
            // Gemini occasionally leaves trailing commas or minor syntax errors —
            // surface a clean error message instead of the raw decoding failure.
            throw NutritionRepositoryError.malformedJSON
        }
    }
}
