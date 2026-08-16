import AppIntents
import ScaScanKit

/// Powers the Control Center "Log by voice" button (see `VoiceLogControl`).
/// This intent runs in this extension's own process, not the app's, so it
/// can't reach the live `AppContainer` directly — it leaves a flag behind in
/// the shared App Group store and posts `VoiceLogSignal` so the app process
/// (whether it needs launching, or is already running — see that type's doc
/// comment for why both paths are needed) knows to check it right away.
/// `openAppWhenRun` additionally tells the system to bring ScaScan to the
/// foreground as part of running this.
struct StartVoiceLogIntent: AppIntent {
    static var title: LocalizedStringResource { "Log by Voice" }
    static var description: IntentDescription {
        IntentDescription("Opens ScaScan and starts listening so you can describe what you're eating.")
    }
    static var openAppWhenRun: Bool { true }

    @MainActor
    func perform() async throws -> some IntentResult {
        UserProfileStore.shared.pendingVoiceLogRequest = true
        VoiceLogSignal.post()
        return .result()
    }
}
