import Foundation

/// Extracts and decodes the first `{...}` JSON object found in a Gemini text
/// response — Gemini is asked to "return ONLY JSON" but sometimes wraps it in
/// markdown fences or a sentence anyway, so both `NutritionRepository.parseResponse`
/// (Kotlin original) and `computeTargets` share this exact substring-then-decode
/// shape. Pulled out to its own `internal` type so it's unit-testable without
/// a network call, unlike when it was a `private` method on the repository.
enum JSONExtraction {
    static func decodeFirstObject<T: Decodable>(_ type: T.Type, from text: String, decoder: JSONDecoder = JSONDecoder()) throws -> T {
        guard let start = text.firstIndex(of: "{"), let end = text.lastIndex(of: "}") else {
            throw NutritionRepositoryError.noJSONInResponse
        }
        let slice = Data(text[start...end].utf8)
        do {
            return try decoder.decode(T.self, from: slice)
        } catch {
            // Gemini occasionally leaves trailing commas or minor syntax errors —
            // surface a clean error message instead of the raw decoding failure.
            throw NutritionRepositoryError.malformedJSON
        }
    }
}
