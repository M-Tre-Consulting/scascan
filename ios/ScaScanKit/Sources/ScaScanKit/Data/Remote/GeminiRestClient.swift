import Foundation
import UIKit

public struct ModelInfo: Sendable, Hashable {
    public let id: String
    public let displayName: String

    public init(id: String, displayName: String) {
        self.id = id
        self.displayName = displayName
    }
}

public enum GeminiClientError: Error, LocalizedError, Sendable {
    case http(Int, String)
    case unexpectedResponse(String)

    public var errorDescription: String? {
        switch self {
        case .http(let code, let body):
            return "Gemini API error \(code) — \(body)"
        case .unexpectedResponse(let raw):
            return "Unexpected Gemini response format: \(raw.prefix(300))"
        }
    }
}

/// Mirrors Android's `data.remote.GeminiRestClient` — a raw REST client against
/// the Gemini `generateContent` endpoint (deliberately no SDK, same as upstream).
public final class GeminiRestClient: Sendable {
    public static let shared = GeminiRestClient()

    private static let apiBase = "https://generativelanguage.googleapis.com/v1beta/models"
    private static let imageMaxSide: CGFloat = 1_024
    private static let imageQuality: CGFloat = 0.85
    private static let maxRetries = 3

    private let session: URLSession

    public init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
    }

    /// Fetches every model available to `apiKey` that supports generateContent.
    public func listModels(apiKey: String) async throws -> [ModelInfo] {
        guard let url = URL(string: "\(Self.apiBase)?key=\(apiKey)") else {
            throw GeminiClientError.unexpectedResponse("Invalid URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw GeminiClientError.http(code, String(data: data, encoding: .utf8) ?? "HTTP \(code)")
        }
        return Self.parseModelList(data)
    }

    private static func parseModelList(_ data: Data) -> [ModelInfo] {
        guard
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let models = json["models"] as? [[String: Any]]
        else { return [] }

        var result: [ModelInfo] = []
        for obj in models {
            // Keep only models that support generateContent (excludes embedding-only models).
            guard let methods = obj["supportedGenerationMethods"] as? [String],
                  methods.contains("generateContent") else { continue }
            guard let name = obj["name"] as? String else { continue } // e.g. "models/gemini-2.5-flash"
            let id = name.hasPrefix("models/") ? String(name.dropFirst("models/".count)) : name
            guard id.hasPrefix("gemini-") else { continue } // exclude AQA, PaLM 2, embedding-001, etc.
            let inputTokenLimit = obj["inputTokenLimit"] as? Int ?? 0
            if (1...8_191).contains(inputTokenLimit) { continue } // exclude Nano and other tiny-context models
            let label = obj["displayName"] as? String ?? id
            result.append(ModelInfo(id: id, displayName: label))
        }
        return result
    }

    public func generateText(model: String, apiKey: String, prompt: String) async throws -> String {
        try await postWithRetry(model: model, apiKey: apiKey, body: Self.textBody(prompt: prompt))
    }

    public func generateWithImage(model: String, apiKey: String, image: UIImage, prompt: String) async throws -> String {
        try await postWithRetry(model: model, apiKey: apiKey, body: Self.imageBody(image: image, prompt: prompt))
    }

    private func postWithRetry(model: String, apiKey: String, body: Data) async throws -> String {
        var retryCount = 0
        var lastError: Error?

        while retryCount <= Self.maxRetries {
            do {
                return try await post(model: model, apiKey: apiKey, body: body)
            } catch {
                lastError = error
                let isRetryable: Bool
                switch error {
                case GeminiClientError.http(let code, _):
                    isRetryable = code == 503 || code == 429
                case let urlError as URLError:
                    isRetryable = urlError.code == .timedOut || urlError.code == .cannotConnectToHost
                default:
                    isRetryable = false
                }

                if isRetryable && retryCount < Self.maxRetries {
                    retryCount += 1
                    let delayMs = 2_000.0 * pow(2.0, Double(retryCount - 1))
                    try? await Task.sleep(for: .milliseconds(delayMs))
                } else {
                    throw error
                }
            }
        }
        throw lastError ?? GeminiClientError.unexpectedResponse("Failed after retries")
    }

    private func post(model: String, apiKey: String, body: Data) async throws -> String {
        guard let url = URL(string: "\(Self.apiBase)/\(model):generateContent?key=\(apiKey)") else {
            throw GeminiClientError.unexpectedResponse("Invalid URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw GeminiClientError.http(code, String(data: data, encoding: .utf8) ?? "(empty error body)")
        }
        return try Self.extractText(data)
    }

    // MARK: - Request builders

    private static func textBody(prompt: String) -> Data {
        let payload: [String: Any] = ["contents": [["parts": [["text": prompt]]]]]
        return (try? JSONSerialization.data(withJSONObject: payload)) ?? Data()
    }

    private static func imageBody(image: UIImage, prompt: String) -> Data {
        let scaled = scale(image)
        let jpeg = scaled.jpegData(compressionQuality: imageQuality) ?? Data()
        let base64 = jpeg.base64EncodedString()

        let payload: [String: Any] = [
            "contents": [[
                "parts": [
                    ["inlineData": ["mimeType": "image/jpeg", "data": base64]],
                    ["text": prompt]
                ]
            ]]
        ]
        return (try? JSONSerialization.data(withJSONObject: payload)) ?? Data()
    }

    private static func scale(_ image: UIImage) -> UIImage {
        let w = image.size.width, h = image.size.height
        guard w > imageMaxSide || h > imageMaxSide else { return image }
        let factor = imageMaxSide / max(w, h)
        let newSize = CGSize(width: w * factor, height: h * factor)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: newSize)) }
    }

    // MARK: - Response parser

    private static func extractText(_ data: Data) throws -> String {
        guard
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let candidates = json["candidates"] as? [[String: Any]],
            let content = candidates.first?["content"] as? [String: Any],
            let parts = content["parts"] as? [[String: Any]],
            let text = parts.first?["text"] as? String
        else {
            throw GeminiClientError.unexpectedResponse(String(data: data, encoding: .utf8) ?? "")
        }
        return text
    }
}
