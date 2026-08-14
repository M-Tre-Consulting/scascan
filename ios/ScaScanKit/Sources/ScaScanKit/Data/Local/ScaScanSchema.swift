import Foundation
import SwiftData

/// Central SwiftData schema definition, shared by the app target, the widget
/// extension, and previews/tests — the single source of truth for which
/// `@Model` types make up the local store (mirrors Android's `AppDatabase`).
public enum ScaScanSchema {
    public static let models: [any PersistentModel.Type] = [
        LogEntry.self,
        WaterLog.self
    ]

    /// Creates the shared `ModelContainer`. Use `inMemory: true` for previews and tests.
    ///
    /// `cloudKitDatabase: .none` is explicit rather than left to the default:
    /// this app carries no CloudKit entitlement (it requires a paid Apple
    /// Developer Program membership to provision a container, which isn't
    /// assumed here), so SwiftData's automatic CloudKit mirroring — which
    /// demands every attribute be optional or defaulted — must never
    /// activate. Local-only storage, shared with the widget extension via
    /// the App Group below.
    public static func makeContainer(inMemory: Bool = false) -> ModelContainer {
        let schema = Schema(models)
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true, cloudKitDatabase: .none)
        } else if let groupURL = AppGroup.sharedContainerURL {
            // Shared with the widget extension — falls back to the app's own
            // sandboxed default location if the App Group isn't configured.
            let storeURL = groupURL.appending(path: "ScaScan.sqlite")
            configuration = ModelConfiguration(schema: schema, url: storeURL, cloudKitDatabase: .none)
        } else {
            configuration = ModelConfiguration(schema: schema, cloudKitDatabase: .none)
        }
        do {
            return try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            fatalError("Failed to create ScaScan ModelContainer: \(error)")
        }
    }
}
