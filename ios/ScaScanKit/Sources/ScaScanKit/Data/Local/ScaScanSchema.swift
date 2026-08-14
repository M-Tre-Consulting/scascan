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
    public static func makeContainer(inMemory: Bool = false) -> ModelContainer {
        let schema = Schema(models)
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)
        do {
            return try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            fatalError("Failed to create ScaScan ModelContainer: \(error)")
        }
    }
}
