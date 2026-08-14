import Foundation
import Observation
import ScaScanKit

/// Shared model-loading logic used by both `ApiKeySetupView` and
/// `AppSettingsView` — mirrors the `ModelState` slice of Android's
/// `ProfileViewModel`, factored out since both Kotlin fragments duplicated it.
@MainActor
@Observable
final class GeminiModelPickerState {
    enum LoadState: Equatable {
        case idle
        case loading
        case ready([ModelInfo])
        case error(String)
    }

    private(set) var loadState: LoadState = .idle
    private(set) var selectedModelID: String

    private let keyStore: GeminiKeyStore
    private let client: GeminiRestClient

    init(keyStore: GeminiKeyStore = .shared, client: GeminiRestClient = .shared) {
        self.keyStore = keyStore
        self.client = client
        self.selectedModelID = keyStore.selectedModel
    }

    var models: [ModelInfo] {
        if case .ready(let models) = loadState { return models }
        return []
    }

    func loadModels() {
        guard keyStore.hasKey() else { return }
        loadState = .loading
        Task {
            do {
                let models = try await client.listModels(apiKey: keyStore.apiKey)
                loadState = .ready(models)
                guard !models.isEmpty else { return }

                // Restore previous selection or default to the first model.
                let hasCurrent = models.contains { $0.id == selectedModelID }
                let chosen = hasCurrent ? selectedModelID : models[0].id
                select(id: chosen)
            } catch {
                loadState = .error(error.localizedDescription)
            }
        }
    }

    func select(id: String) {
        selectedModelID = id
        keyStore.selectedModel = id
    }
}
