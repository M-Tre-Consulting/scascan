import SwiftUI
import CloudKit
import ScaScanKit

/// Mirrors Android's `ApiKeyFragment` — first-run setup: paste a Gemini API
/// key, pick a model, get started. Returning-user "update key" happens inline
/// in Settings instead of pushing this screen again (see `AppSettingsView`).
struct ApiKeySetupView: View {
    let onFinished: () -> Void

    @Environment(AppContainer.self) private var container
    @State private var apiKeyInput = ""
    @State private var keyError: String?
    @State private var picker = GeminiModelPickerState()
    @State private var keyStore = GeminiKeyStore.shared
    @State private var isSyncing = false
    @State private var syncMessage: String?
    @State private var iCloudAvailable = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header

                    SectionCard {
                        VStack(alignment: .leading, spacing: 10) {
                            TextField("Gemini API key", text: $apiKeyInput)
                                #if os(iOS)
                                .textInputAutocapitalization(.never)
                                #endif
                                .autocorrectionDisabled()
                                .textFieldStyle(.roundedBorder)

                            if let keyError {
                                Text(keyError)
                                    .font(.caption)
                                    .foregroundStyle(.red)
                            }

                            HStack {
                                Button("Save and continue", action: attemptSaveKey)
                                    .buttonStyle(.borderedProminent)
                                Link("Get my free key", destination: URL(string: "https://aistudio.google.com/app/apikey")!)
                                    .font(.subheadline)
                            }
                        }
                    }

                    if picker.loadState != .idle {
                        modelSection
                    }

                    syncSection

                    Text("Where do I find my key? Go to aistudio.google.com → Get API key. The key is stored locally on this device only and never sent anywhere except to the Gemini API.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    Spacer(minLength: 8)

                    Button("Get started", action: onFinished)
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                        .frame(maxWidth: .infinity)
                        .disabled(!canFinish)
                }
                .padding(20)
            }
            .navigationTitle("Set up your API key")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if keyStore.hasKey() {
                    apiKeyInput = keyStore.apiKey
                    picker.loadModels()
                }
                Task { iCloudAvailable = await container.syncManager.accountStatus() == .available }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("ScaScan uses Google Gemini to analyze food. Paste your Gemini API key below to get started.")
                .foregroundStyle(.secondary)
        }
    }

    private var canFinish: Bool {
        keyStore.hasKey() && !keyStore.selectedModel.isEmpty
    }

    @ViewBuilder
    private var modelSection: some View {
        SectionCard(title: "Choose a model", subtitle: "Select the Gemini model that will analyse your food.") {
            switch picker.loadState {
            case .idle:
                EmptyView()
            case .loading:
                HStack {
                    ProgressView()
                    Text("Loading available models…")
                        .foregroundStyle(.secondary)
                }
            case .ready(let models) where models.isEmpty:
                Text("No models found for this API key")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            case .ready(let models):
                Picker("Gemini model", selection: Binding(
                    get: { picker.selectedModelID },
                    set: { picker.select(id: $0) }
                )) {
                    ForEach(models, id: \.id) { model in
                        Text(model.displayName).tag(model.id)
                    }
                }
                .pickerStyle(.menu)
            case .error(let message):
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
    }

    private var syncSection: some View {
        SectionCard(title: "iCloud Sync") {
            VStack(alignment: .leading, spacing: 8) {
                if iCloudAvailable {
                    Button(isSyncing ? "Syncing…" : "Sync now", action: startSync)
                        .buttonStyle(.bordered)
                        .disabled(isSyncing)
                } else {
                    HStack {
                        Image(systemName: "icloud.slash")
                        Text("Sign in to iCloud in Settings to sync")
                            .foregroundStyle(.secondary)
                        Spacer()
                    }
                }
                if let syncMessage {
                    Text(syncMessage).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private func attemptSaveKey() {
        let key = apiKeyInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else {
            keyError = "Please enter a valid API key"
            return
        }
        keyError = nil
        keyStore.apiKey = key
        picker.loadModels()
    }

    private func startSync() {
        isSyncing = true
        syncMessage = nil
        Task {
            do {
                try await container.syncManager.sync()
                syncMessage = "Sync complete"
            } catch {
                syncMessage = error.localizedDescription
            }
            isSyncing = false
        }
    }
}
