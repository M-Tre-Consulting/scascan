import SwiftUI
import UserNotifications
import ScaScanKit

/// Mirrors Android's `AppSettingsFragment` — API key editing, AI model
/// selection, Health/iCloud connections, and the hydration reminder toggle.
///
/// Health Connect → HealthKit and Google Drive → CloudKit both show as
/// "coming in Phase 4" here: their real UI shape is already in place so
/// wiring the actual frameworks later doesn't require restructuring the screen.
struct AppSettingsView: View {
    @Environment(AppContainer.self) private var container
    @State private var picker = GeminiModelPickerState()
    @State private var apiKeyInput = ""
    @State private var keyError: String?
    @State private var waterRemindersEnabled = false
    @State private var keySavedMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                apiKeySection
                modelSection
                healthSection
                syncSection
                notificationsSection
            }
            .padding(20)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            apiKeyInput = container.keyStore.apiKey
            waterRemindersEnabled = container.profileStore.waterRemindersEnabled
            if container.keyStore.hasKey() { picker.loadModels() }
        }
    }

    private var apiKeySection: some View {
        SectionCard(title: "Gemini API Key", subtitle: "ScaScan uses Google Gemini to analyse food. Get your key at aistudio.google.com.") {
            VStack(alignment: .leading, spacing: 8) {
                TextField("Gemini API key", text: $apiKeyInput)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)
                    .onSubmit(saveKey)

                if let keyError {
                    Text(keyError).font(.caption).foregroundStyle(.red)
                }
                if let keySavedMessage {
                    Text(keySavedMessage).font(.caption).foregroundStyle(.secondary)
                }

                Button("Save key", action: saveKey)
                    .buttonStyle(.borderedProminent)
            }
        }
    }

    @ViewBuilder
    private var modelSection: some View {
        SectionCard(title: "AI Model", subtitle: "Select the Gemini model used for food analysis. Models are fetched live from your API key.") {
            switch picker.loadState {
            case .idle:
                Text("Save your API key first to load available models")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            case .loading:
                HStack { ProgressView(); Text("Loading available models…").foregroundStyle(.secondary) }
            case .ready(let models) where models.isEmpty:
                Text("No models found for this API key")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            case .ready(let models):
                Picker("Select model", selection: Binding(
                    get: { picker.selectedModelID },
                    set: { picker.select(id: $0) }
                )) {
                    ForEach(models, id: \.id) { model in
                        Text(model.displayName).tag(model.id)
                    }
                }
                .pickerStyle(.menu)
            case .error(let message):
                Text(message).font(.footnote).foregroundStyle(.red)
            }
        }
    }

    private var healthSection: some View {
        SectionCard(title: "Apple Health", subtitle: "Connect Health data to enable calorie tracking and adaptive daily goals.") {
            HStack {
                Image(systemName: "heart.fill").foregroundStyle(.pink)
                Text("Coming in Phase 4")
                    .foregroundStyle(.secondary)
                Spacer()
            }
        }
    }

    private var syncSection: some View {
        SectionCard(title: "Cloud Sync", subtitle: "Keep your logs and profile in sync across your devices using iCloud.") {
            HStack {
                Image(systemName: "icloud")
                Text("Coming in Phase 4")
                    .foregroundStyle(.secondary)
                Spacer()
            }
        }
    }

    private var notificationsSection: some View {
        SectionCard(title: "Notifications") {
            Toggle(isOn: Binding(
                get: { waterRemindersEnabled },
                set: { setWaterRemindersEnabled($0) }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Hydration reminder")
                    Text("Receive periodic notifications to remind you to drink water during the day.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private func saveKey() {
        let trimmed = apiKeyInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            keyError = "Please enter a valid API key"
            return
        }
        keyError = nil
        container.keyStore.apiKey = trimmed
        keySavedMessage = "API key saved"
        picker.loadModels()
    }

    private func setWaterRemindersEnabled(_ enabled: Bool) {
        waterRemindersEnabled = enabled
        container.profileStore.waterRemindersEnabled = enabled

        guard enabled else { return }
        Task {
            let center = UNUserNotificationCenter.current()
            let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
            if !granted {
                waterRemindersEnabled = false
                container.profileStore.waterRemindersEnabled = false
            }
            // TODO(Phase 4): schedule the recurring reminder via BGTaskScheduler,
            // mirroring Android's WorkManager-backed HydrationReminderWorker.
        }
    }
}
