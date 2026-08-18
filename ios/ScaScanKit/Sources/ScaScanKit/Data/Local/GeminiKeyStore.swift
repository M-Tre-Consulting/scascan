import Foundation
import Security

/// Mirrors Android's `data.local.GeminiKeyStore`, with one deliberate upgrade:
/// the API key lives in the Keychain instead of plaintext SharedPreferences.
/// `selectedModel` stays in UserDefaults — it isn't a secret.
public final class GeminiKeyStore: @unchecked Sendable {
    public static let shared = GeminiKeyStore()

    private let service = "com.scascan.app.gemini"
    private let account = "gemini-api-key"
    private let defaults: UserDefaults
    private let modelKey = "gemini_model"

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var apiKey: String {
        get { readKeychain() ?? "" }
        set { writeKeychain(newValue.trimmingCharacters(in: .whitespacesAndNewlines)) }
    }

    /// Persists the last model the user chose. Falls back to an empty string
    /// so the app can detect "no model selected yet" and prompt the user in Profile.
    public var selectedModel: String {
        get { defaults.string(forKey: modelKey) ?? "" }
        set { defaults.set(newValue, forKey: modelKey) }
    }

    public func hasKey() -> Bool { !apiKey.trimmingCharacters(in: .whitespaces).isEmpty }

    // MARK: - Keychain plumbing

    private func readKeychain() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func writeKeychain(_ value: String) {
        let data = Data(value.utf8)
        let baseQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]

        if readKeychain() != nil {
            SecItemUpdate(baseQuery as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        } else {
            var newItem = baseQuery
            newItem[kSecValueData as String] = data
            newItem[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
            SecItemAdd(newItem as CFDictionary, nil)
        }
    }
}
