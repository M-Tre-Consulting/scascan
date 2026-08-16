import SwiftUI
import WidgetKit
import AppIntents

/// A Control Center tile (iOS 18+ ControlKit) that opens ScaScan straight
/// into voice logging — no need to open the app first, tap a card, then
/// speak; one tap here does all three. See `StartVoiceLogIntent` for the
/// hand-off to the app process.
///
/// Lives in its own extension target (`ScaScanControl`), separate from the
/// home screen widget's `ScaScanWidgetExtension`: `ControlWidget` is a
/// distinct protocol from `Widget` with its own `@main`-style entry point
/// (`static func main()`) and isn't embeddable inside a `WidgetBundle`'s
/// `body` — a first attempt at declaring this as a second, non-`@main` type
/// inside `ScaScanWidgetExtension` alongside `ScaScanWidgetBundle` compiled
/// fine but was silently never registered by the system (Control Center
/// never listed it) since nothing ever ran its `main()`. Both extensions use
/// the same `com.apple.widgetkit-extension` extension point; they just can't
/// share a target when each needs to be its own entry point.
@main
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
