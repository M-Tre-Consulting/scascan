import SwiftUI
import WidgetKit
import AppIntents

/// A Control Center tile (iOS 18+ ControlKit) that opens ScaScan straight
/// into voice logging — no need to open the app first, tap a card, then
/// speak; one tap here does all three. See `StartVoiceLogIntent` for the
/// hand-off to the app process.
///
/// Not part of `ScaScanWidgetBundle`: `ControlWidget` is a distinct protocol
/// from `Widget` (its own `main()`, not embeddable in a `WidgetBundle`'s
/// body) but registers from the same `com.apple.widgetkit-extension`
/// extension point, so it lives here as a standalone declaration in the same
/// target rather than a second `@main`.
struct VoiceLogControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "com.nicoloperri.Scascan.VoiceLogControl") {
            ControlWidgetButton(action: StartVoiceLogIntent()) {
                Label("Log by Voice", systemImage: "mic.fill")
            }
        }
        .displayName("Log by Voice")
        .description("Opens ScaScan and starts listening so you can describe what you're eating.")
    }
}
