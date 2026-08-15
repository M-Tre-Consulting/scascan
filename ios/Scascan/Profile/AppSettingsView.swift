import SwiftUI
import ScaScanKit

/// Mirrors Android's `AppSettingsFragment` — API key editing, AI model
/// selection, the Apple Health connection, and the hydration reminder toggle.
///
/// No cloud sync section: Android's Google Drive backup would map to
/// CloudKit here, but CloudKit containers require a paid Apple Developer
/// Program membership to provision — not available on a free personal team.
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

    @State private var waterButton1 = 100
    @State private var waterButton2 = 250
    @State private var waterButton3 = 500
    @State private var widgetWaterAmount = 250

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                apiKeySection
                modelSection
                healthSection
                waterSection
                notificationsSection
            }
            .padding(20)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            apiKeyInput = container.keyStore.apiKey
            waterRemindersEnabled = container.profileStore.waterRemindersEnabled
            waterButton1 = container.profileStore.waterButton1Ml
            waterButton2 = container.profileStore.waterButton2Ml
            waterButton3 = container.profileStore.waterButton3Ml
            widgetWaterAmount = container.profileStore.widgetWaterAmountMl
            if container.keyStore.hasKey() { picker.loadModels() }
            Task { healthConnected = await container.healthManager.hasPermissions() }
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
        SectionCard(
            title: "Apple Health",
            subtitle: "Reads steps, active calories, weight, height, and Apple Watch/Fitness workouts to power adaptive daily goals. Writes every meal and glass of water you log here back to Health, so your intake shows up in Health's Nutrition tab too and workout calories count toward today's target automatically."
        ) {
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

    private var waterSection: some View {
        SectionCard(title: "Water amounts", subtitle: "100/250/500ml won't fit everyone's glass or bottle — set your own quick-add amounts here, for both the Log tab and the home screen widget.") {
            VStack(alignment: .leading, spacing: 4) {
                waterStepper("Quick-add button 1", value: $waterButton1) { container.profileStore.waterButton1Ml = $0 }
                waterStepper("Quick-add button 2", value: $waterButton2) { container.profileStore.waterButton2Ml = $0 }
                waterStepper("Quick-add button 3", value: $waterButton3) { container.profileStore.waterButton3Ml = $0 }
                Divider().padding(.vertical, 4)
                waterStepper("Widget quick-add", value: $widgetWaterAmount) { container.profileStore.widgetWaterAmountMl = $0 }
            }
        }
    }

    private func waterStepper(_ label: String, value: Binding<Int>, onChange: @escaping (Int) -> Void) -> some View {
        Stepper(value: value, in: 25...2_000, step: 25) {
            HStack {
                Text(label)
                Spacer()
                Text("\(value.wrappedValue) ml").foregroundStyle(.secondary)
            }
        }
        .onChange(of: value.wrappedValue) { _, newValue in onChange(newValue) }
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
