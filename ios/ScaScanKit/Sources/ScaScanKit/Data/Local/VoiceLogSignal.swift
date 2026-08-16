import Foundation

/// A cross-process "wake up and check now" signal for the Control Center →
/// app hand-off (`StartVoiceLogIntent` posts, `AppContainer` observes).
///
/// The actual request is carried by `UserProfileStore.pendingVoiceLogRequest`
/// (an App Group UserDefaults flag) — this signal exists only because
/// UserDefaults gives no delivery-timing guarantee, and SwiftUI's
/// `scenePhase` doesn't necessarily change at all if the app was already
/// active in the foreground underneath Control Center's overlay when the
/// button was tapped (in which case nothing would ever prompt the app to
/// re-check the flag). A Darwin notification is the one mechanism on iOS
/// that reliably and immediately crosses processes regardless of either of
/// those, so it's used purely as the "go look at the flag now" nudge.
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
