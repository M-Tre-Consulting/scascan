import Foundation

/// A "check the voice-log request flag right now" signal
/// (`StartVoiceLogIntent` posts, `AppContainer` observes).
///
/// The request itself is carried by `UserProfileStore.pendingVoiceLogRequest`;
/// this exists only to prompt an immediate re-read of it. Needed because
/// SwiftUI's `scenePhase` doesn't necessarily change at all when the intent
/// runs while the app is already active in the foreground — with nothing else
/// to react to, the flag could sit unnoticed. A Darwin notification is
/// delivered immediately regardless of app lifecycle state (and regardless of
/// which process posts it), so it covers that case cleanly.
public enum VoiceLogSignal {
    private static var name: CFString { "com.nicoloperri.Scascan.voiceLogRequested" as CFString }

    public static func post() {
        CFNotificationCenterPostNotification(
            CFNotificationCenterGetDarwinNotifyCenter(), CFNotificationName(name), nil, nil, true
        )
    }

    /// Registers `handler` to run (on an arbitrary thread — hop to whatever
    /// actor you need yourself) whenever the signal is posted from any
    /// process, including this one. Only one handler is supported; call once
    /// per process, at launch.
    public static func observe(_ handler: @escaping @Sendable () -> Void) {
        Box.handler = handler
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            nil,
            { _, _, _, _, _ in Box.handler?() },
            name,
            nil,
            .deliverImmediately
        )
    }

    private enum Box {
        nonisolated(unsafe) static var handler: (@Sendable () -> Void)?
    }
}
