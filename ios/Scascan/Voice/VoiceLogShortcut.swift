import AppIntents
import ScaScanKit

/// Siri / Shortcuts entry point for voice logging: "Hey Siri, log by voice
/// with ScaScan" foregrounds the app straight into `VoiceSearchView`, already
/// listening, so the user can just say what they ate.
///
/// This replaces an earlier Control Center button that never worked. The key
/// difference isn't Siri as such — it's that this intent lives in the **main
/// app target**, so `perform()` runs inside the app's own process. The
/// Control Center version had to live in a separate extension target (a
/// `ControlWidget` needs its own entry point), which meant it could only
/// leave a flag in shared storage and hope the app noticed it, with no
/// ordering guarantee between the two processes — the part that kept failing.
/// Here the intent runs where `AppContainer` already lives, so the hand-off
/// is a direct in-process one.
struct StartVoiceLogIntent: AppIntent {
    static var title: LocalizedStringResource { "Log by Voice" }
    static var description: IntentDescription {
        IntentDescription("Opens ScaScan and starts listening so you can describe what you're eating.")
    }
    /// Brings ScaScan to the foreground as part of running this — voice
    /// logging is inherently a "put the app on screen and listen" action, not
    /// something that can complete in the background.
    static var openAppWhenRun: Bool { true }

    @MainActor
    func perform() async throws -> some IntentResult {
        // Same shared flag + signal pair the app already checks on launch,
        // foreground, and on demand — see `VoiceLogSignal`. Running
        // in-process, the signal is delivered to this app's own observer
        // immediately, which covers the case where the app was already
        // foregrounded (no scene-phase change to react to).
        UserProfileStore.shared.pendingVoiceLogRequest = true
        VoiceLogSignal.post()
        return .result()
    }
}

/// Registers the spoken phrases with Siri. Every phrase must contain
/// `\(.applicationName)` — Siri requires the app name in App Shortcut
/// phrases, which is why they all read "… with ScaScan" rather than a bare
/// command. These are surfaced automatically in the Shortcuts app and in
/// Spotlight too, no user setup needed beyond installing the app.
struct ScascanAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: StartVoiceLogIntent(),
            phrases: [
                "Log by voice with \(.applicationName)",
                "Log food with \(.applicationName)",
                "Log a meal with \(.applicationName)",
                "Voice log with \(.applicationName)",
                "Track food with \(.applicationName)"
            ],
            shortTitle: "Log by Voice",
            systemImageName: "mic.fill"
        )
    }
}
