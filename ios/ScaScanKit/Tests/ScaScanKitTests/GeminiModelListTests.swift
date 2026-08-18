import Testing
import Foundation
@testable import ScaScanKit

@Suite("GeminiRestClient.parseModelList — model filtering")
struct GeminiModelListTests {
    private func modelsJSON(_ entries: String) -> Data {
        Data("{\"models\": [\(entries)]}".utf8)
    }

    @Test("Keeps a usable Gemini model and strips the 'models/' prefix")
    func keepsUsableModel() {
        let json = modelsJSON("""
        {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent","countTokens"]}
        """)
        let models = GeminiRestClient.parseModelList(json)
        #expect(models.count == 1)
        #expect(models.first?.id == "gemini-2.5-flash")
        #expect(models.first?.displayName == "Gemini 2.5 Flash")
    }

    @Test("Excludes models that don't support generateContent (embedding-only)")
    func excludesEmbeddingOnly() {
        let json = modelsJSON("""
        {"name":"models/gemini-embedding-001","displayName":"Gemini Embedding","inputTokenLimit":2048,"supportedGenerationMethods":["embedContent"]}
        """)
        #expect(GeminiRestClient.parseModelList(json).isEmpty)
    }

    @Test("Excludes non-Gemini models (AQA, PaLM 2, etc.)")
    func excludesNonGemini() {
        let json = modelsJSON("""
        {"name":"models/aqa","displayName":"AQA","inputTokenLimit":7168,"supportedGenerationMethods":["generateAnswer","generateContent"]}
        """)
        #expect(GeminiRestClient.parseModelList(json).isEmpty)
    }

    @Test("Excludes tiny-context models like Nano (1...8191 token range)")
    func excludesTinyContext() {
        let json = modelsJSON("""
        {"name":"models/gemini-nano","displayName":"Gemini Nano","inputTokenLimit":4096,"supportedGenerationMethods":["generateContent"]}
        """)
        #expect(GeminiRestClient.parseModelList(json).isEmpty)
    }

    @Test("A zero inputTokenLimit is NOT excluded by the tiny-context rule (1...8191 range only)")
    func zeroTokenLimitNotExcluded() {
        let json = modelsJSON("""
        {"name":"models/gemini-2.5-pro","displayName":"Gemini 2.5 Pro","supportedGenerationMethods":["generateContent"]}
        """)
        // No inputTokenLimit field at all -> defaults to 0, which is outside 1...8191.
        #expect(GeminiRestClient.parseModelList(json).count == 1)
    }

    @Test("Falls back to the model id when displayName is missing")
    func fallsBackToId() {
        let json = modelsJSON("""
        {"name":"models/gemini-3.5-flash","inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent"]}
        """)
        #expect(GeminiRestClient.parseModelList(json).first?.displayName == "gemini-3.5-flash")
    }

    @Test("Filters a realistic mixed batch down to just the usable Gemini models")
    func mixedBatch() {
        let json = modelsJSON("""
        {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent","countTokens"]},
        {"name":"models/gemini-embedding-001","displayName":"Gemini Embedding","inputTokenLimit":2048,"supportedGenerationMethods":["embedContent"]},
        {"name":"models/aqa","displayName":"AQA","inputTokenLimit":7168,"supportedGenerationMethods":["generateAnswer"]},
        {"name":"models/gemini-3.7-flash","displayName":"Gemini 3.7 Flash","inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent","countTokens"]}
        """)
        let ids = GeminiRestClient.parseModelList(json).map(\.id).sorted()
        #expect(ids == ["gemini-2.5-flash", "gemini-3.7-flash"])
    }
}
