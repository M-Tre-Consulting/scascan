import SwiftUI
import UIKit

/// The app's brand green — same hue family as the app icon's background
/// (#386A20), tuned separately for light/dark so it stays legible as a UI
/// tint in both: a deeper, richer green on light backgrounds, a brighter one
/// on dark. This mirrors exactly what the app target's own
/// `Assets.xcassets/AccentColor.colorset` defines.
///
/// The widget extension runs in its own process with its own bundle, so it
/// can't read the app target's asset catalog — this code-based color is the
/// shared source of truth both the app and the widget can reach, living here
/// in ScaScanKit rather than duplicated in each target.
public extension Color {
    static let scascanBrand = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0x7F / 255, green: 0xCF / 255, blue: 0x59 / 255, alpha: 1)
            : UIColor(red: 0x3D / 255, green: 0x79 / 255, blue: 0x20 / 255, alpha: 1)
    })
}
