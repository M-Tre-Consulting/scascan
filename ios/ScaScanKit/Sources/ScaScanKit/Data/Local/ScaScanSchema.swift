import Foundation
import SwiftData
import OSLog

/// Central SwiftData schema definition, shared by the app target, the widget
/// extension, and previews/tests — the single source of truth for which
/// `@Model` types make up the local store (mirrors Android's `AppDatabase`).
public enum ScaScanSchema {
    public static let models: [any PersistentModel.Type] = [
        LogEntry.self,
        WaterLog.self
    ]

    private static let logger = Logger(subsystem: "com.nicoloperri.Scascan", category: "ScaScanSchema")

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
        let storeURL: URL? = inMemory ? nil : (AppGroup.sharedContainerURL?.appending(path: "ScaScan.sqlite"))
        let configuration = makeConfiguration(schema: schema, storeURL: storeURL, inMemory: inMemory)

        do {
            return try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            // A store that fails to open once (e.g. a lightweight migration
            // SwiftData can't infer, or genuine on-disk corruption) will fail
            // identically on every future launch too — left as `fatalError`,
            // this was an unrecoverable crash-loop with no way for the user
            // to get back into the app at all (this is exactly what
            // happened: `SwiftDataError.loadIssueModelContainer` on launch,
            // from LogEntry/WaterLog gaining a new non-optional `id` column
            // mid-flight). So: on a real on-disk store, fall back to
            // discarding it and starting fresh rather than crashing forever.
            // Losing local log history in this rare case beats a bricked app.
            logger.error("ModelContainer failed to load (\(error.localizedDescription, privacy: .public)) — recreating store")
            guard !inMemory, let storeURL else {
                fatalError("Failed to create in-memory ScaScan ModelContainer: \(error)")
            }
            removeStoreFiles(at: storeURL)
            let freshConfiguration = makeConfiguration(schema: schema, storeURL: storeURL, inMemory: false)
            do {
                return try ModelContainer(for: schema, configurations: [freshConfiguration])
            } catch {
                fatalError("Failed to create ScaScan ModelContainer even after recreating the store: \(error)")
            }
        }
    }

    private static func makeConfiguration(schema: Schema, storeURL: URL?, inMemory: Bool) -> ModelConfiguration {
        if inMemory {
            return ModelConfiguration(schema: schema, isStoredInMemoryOnly: true, cloudKitDatabase: .none)
        } else if let storeURL {
            // Shared with the widget extension — falls back to the app's own
            // sandboxed default location if the App Group isn't configured.
            return ModelConfiguration(schema: schema, url: storeURL, cloudKitDatabase: .none)
        } else {
            return ModelConfiguration(schema: schema, cloudKitDatabase: .none)
        }
    }

    /// SQLite keeps the store as three sibling files (`.sqlite`, `-wal`,
    /// `-shm`) — all three need to go, or the leftover WAL/SHM can make the
    /// "fresh" store fail to open too.
    private static func removeStoreFiles(at storeURL: URL) {
        let fm = FileManager.default
        for suffix in ["", "-wal", "-shm"] {
            try? fm.removeItem(at: URL(fileURLWithPath: storeURL.path + suffix))
        }
    }
}
