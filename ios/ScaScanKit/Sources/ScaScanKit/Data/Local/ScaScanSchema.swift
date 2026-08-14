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
    /// `cloudKitDatabase: .none` is required, not cosmetic: once the app
    /// carries the CloudKit entitlement (for `CloudKitSyncManager`'s own,
    /// manual sync), `ModelConfiguration` otherwise auto-detects it and turns
    /// on SwiftData's *own* CloudKit mirroring — a separate mechanism that
    /// demands every attribute be optional or defaulted, which crashes this
    /// schema at launch. We're syncing by hand, so that automatic mirroring
    /// is explicitly opted out of.
    public static func makeContainer(inMemory: Bool = false) -> ModelContainer {
        let schema = Schema(models)
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory, cloudKitDatabase: .none)
        do {
            return try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            fatalError("Failed to create ScaScan ModelContainer: \(error)")
        }
    }
}
