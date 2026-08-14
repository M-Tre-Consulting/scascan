import SwiftUI
import CloudKit
import ScaScanKit

/// Mirrors Android's `AppSettingsFragment` — API key editing, AI model
/// selection, Health/iCloud connections, and the hydration reminder toggle.
struct AppSettingsView: View {
    @Environment(AppContainer.self) private var container
    @State private var picker = GeminiModelPickerState()
    @State private var apiKeyInput = ""
    @State private var keyError: String?
    @State private var waterRemindersEnabled = false
    @State private var keySavedMessage: String?

    @State private var healthConnected = false
    @State private var isSyncingWeight = false
    @State private var healthMessage: String?

    @State private var isSyncing = false
    @State private var syncMessage: String?
    @State private var iCloudAvailable = false

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
            Task { healthConnected = await container.healthManager.hasPermissions() }
            Task { iCloudAvailable = await container.syncManager.accountStatus() == .available }
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
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "heart.fill").foregroundStyle(.pink)
                    Text(healthConnected ? "Connected" : "Disconnected")
                        .foregroundStyle(.secondary)
                    Spacer()
                }

                if let healthMessage {
                    Text(healthMessage).font(.caption).foregroundStyle(.secondary)
                }

                if healthConnected {
                    HStack(spacing: 10) {
                        Button(isSyncingWeight ? "Syncing…" : "Sync weight & height", action: syncFromHealth)
                            .buttonStyle(.bordered)
                            .disabled(isSyncingWeight)
                        Button("Disconnect", role: .destructive, action: disconnectHealth)
                            .buttonStyle(.bordered)
                    }
                } else {
                    Button("Connect Apple Health", action: connectHealth)
                        .buttonStyle(.borderedProminent)
                }
            }
        }
    }

    private var syncSection: some View {
        SectionCard(title: "Cloud Sync", subtitle: "Keep your logs and profile in sync across your devices using iCloud.") {
            VStack(alignment: .leading, spacing: 10) {
                if iCloudAvailable {
                    Button(isSyncing ? "Syncing…" : "Sync now", action: startSync)
                        .buttonStyle(.borderedProminent)
                        .disabled(isSyncing)
                } else {
                    Text("Sign in to iCloud in Settings to enable sync.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if let syncMessage {
                    Text(syncMessage).font(.caption).foregroundStyle(.secondary)
                }
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

    private func connectHealth() {
        guard container.healthManager.isAvailable else {
            healthMessage = "Health data isn't available on this device."
            return
        }
        Task {
            do {
                try await container.healthManager.requestAuthorization()
                healthConnected = true
                healthMessage = nil
            } catch {
                healthMessage = error.localizedDescription
            }
        }
    }

    private func disconnectHealth() {
        container.healthManager.disconnect()
        healthConnected = false
        healthMessage = "Disconnected locally — to fully revoke access, open Health ▸ Data Access & Devices."
    }

    private func syncFromHealth() {
        isSyncingWeight = true
        Task {
            let kg = await container.healthManager.readLatestWeightKg()
            let cm = await container.healthManager.readLatestHeightCm()
            isSyncingWeight = false

            guard kg != nil || cm != nil else {
                healthMessage = "No weight data found in Health"
                return
            }
            if let kg { container.profileStore.weightKg = kg }
            if let cm { container.profileStore.heightCm = Int(cm) }
            healthMessage = "Weight & height synced"
        }
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

    private func setWaterRemindersEnabled(_ enabled: Bool) {
        waterRemindersEnabled = enabled
        container.profileStore.waterRemindersEnabled = enabled

        guard enabled else {
            NotificationHelper.cancelHydrationReminder()
            return
        }
        Task {
            let granted = await NotificationHelper.requestAuthorization()
            if granted {
                // Every 3 hours, matching Android's ReminderManager interval.
                NotificationHelper.scheduleHydrationReminder(interval: 3 * 60 * 60)
            } else {
                waterRemindersEnabled = false
                container.profileStore.waterRemindersEnabled = false
            }
        }
    }
}
