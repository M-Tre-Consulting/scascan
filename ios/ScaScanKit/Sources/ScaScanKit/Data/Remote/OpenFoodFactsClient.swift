import Foundation

/// Mirrors Android's `data.remote.OpenFoodFactsClient`.
public final class OpenFoodFactsClient: Sendable {
    public static let shared = OpenFoodFactsClient()

    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    /// Fetches product data from OpenFoodFacts for a given barcode.
    /// Returns a JSON string of the product data, or nil if not found.
    public func getProduct(barcode: String) async -> String? {
        guard let url = URL(string: "https://world.openfoodfacts.org/api/v2/product/\(barcode).json") else {
            return nil
        }
        var request = URLRequest(url: url)
        request.setValue("ScaScan - iOS - Version 1.0", forHTTPHeaderField: "User-Agent")

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            guard
                let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                (json["status"] as? Int) == 1,
                let product = json["product"]
            else { return nil }
            // Extract only the product portion to keep the prompt size manageable.
            let productData = try JSONSerialization.data(withJSONObject: product)
            return String(data: productData, encoding: .utf8)
        } catch {
            return nil
        }
    }
}
