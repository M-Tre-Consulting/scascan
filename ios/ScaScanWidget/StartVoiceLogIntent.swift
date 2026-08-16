import AppIntents
import ScaScanKit

/// Powers the Control Center "Log by voice" button (see `VoiceLogControl`).
/// `openAppWhenRun` foregrounds ScaScan and runs `perform()` there — this
/// can't reach the live `AppContainer` directly (it's a separate process
/// until the app is actually opened), so it just leaves a flag behind in the
/// shared App Group store. `ScascanApp` checks that flag on every foreground
/// and, when set, opens straight into `VoiceSearchView`, already listening.
struct StartVoiceLogIntent: AppIntent {
    static var title: LocalizedStringResource { "Log by Voice" }
    static var description: IntentDescription {
        IntentDescription("Opens ScaScan and starts listening so you can describe what you're eating.")
    }
    static var openAppWhenRun: Bool { true }

    @MainActor
    func perform() async throws -> some IntentResult {
        UserProfileStore.shared.pendingVoiceLogRequest = true
        return .result()
    }
}
