import Foundation

/// The App Group shared between the ScaScan app and its home screen widget
/// extension — the shared SwiftData store and `UserDefaults` (profile,
/// targets) both live here so the widget can read live data without going
/// through the app process. There's no Android analogue: a home screen
/// widget there just calls straight into the same process's repositories.
public enum AppGroup {
    public static let identifier = "group.com.nicoloperri.Scascan"

    public static var sharedDefaults: UserDefaults {
        UserDefaults(suiteName: identifier) ?? .standard
    }

    public static var sharedContainerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }
}
