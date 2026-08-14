import Foundation
import CloudKit

/// Mirrors Android's `data.sync.DriveSyncManager`, backed by this app's
/// private CloudKit database instead of a file in Google Drive's
/// `appDataFolder` — both are the platform's per-app, user-private storage,
/// so the swap is conceptually direct. One genuine simplification: CloudKit
/// authenticates via the device's signed-in iCloud account automatically,
/// so there's no separate OAuth/sign-in flow to build (Android needs
/// `AuthorizationRequest`/`Identity.getAuthorizationClient` for exactly this).
@MainActor
public final class CloudKitSyncManager {
    public enum SyncError: Error, LocalizedError {
        case notSignedIn
        case restricted

        public var errorDescription: String? {
            switch self {
            case .notSignedIn: return "Sign in to iCloud in Settings to enable sync."
            case .restricted: return "iCloud access is restricted on this device."
            }
        }
    }

    private static let recordType = "ScaScanSyncData"
    private static let recordID = CKRecord.ID(recordName: "sync-data")
    private static let payloadKey = "payload"
    private static let lastUpdatedKey = "lastUpdated"

    private let container: CKContainer
    private let logRepository: LogRepository
    private let profileStore: UserProfileStore

    public init(
        containerIdentifier: String = "iCloud.com.nicoloperri.Scascan",
        logRepository: LogRepository,
        profileStore: UserProfileStore = .shared
    ) {
        self.container = CKContainer(identifier: containerIdentifier)
        self.logRepository = logRepository
        self.profileStore = profileStore
    }

    public func accountStatus() async -> CKAccountStatus {
        (try? await container.accountStatus()) ?? .couldNotDetermine
    }

    public func sync() async throws {
        switch await accountStatus() {
        case .available: break
        case .restricted, .couldNotDetermine, .temporarilyUnavailable: throw SyncError.restricted
        default: throw SyncError.notSignedIn
        }

        let db = container.privateCloudDatabase

        // 1. Download existing data from CloudKit.
        let existingData = await downloadData(db: db)

        // 2. Merge local data with remote data — deduplicate by timestamp,
        //    preferring the local copy, exactly like Android's
        //    `(localLogs + existingData.logs).distinctBy { it.timestamp }`.
        let localEntries = try logRepository.allEntries()
        let localExports = localEntries.map(LogEntryExport.init)
        let localTimestamps = Set(localExports.map(\.timestamp))

        let remoteOnly = existingData?.logs.filter { !localTimestamps.contains($0.timestamp) } ?? []
        let mergedLogs = localExports + remoteOnly

        // 3. Write the remote-only entries back into the local store.
        if !remoteOnly.isEmpty {
            try logRepository.upsertEntries(remoteOnly.map(\.asLogEntry))
        }

        // 4. Export the local profile (Android does the same — no remote merge).
        let profile = ProfileExport(from: profileStore)

        // 5. Upload the merged data back to CloudKit.
        let syncData = SyncData(profile: profile, logs: mergedLogs, lastUpdated: .now)
        try await uploadData(db: db, data: syncData)
    }

    private func downloadData(db: CKDatabase) async -> SyncData? {
        guard
            let record = try? await db.record(for: Self.recordID),
            let data = record[Self.payloadKey] as? Data
        else { return nil }
        return try? JSONDecoder().decode(SyncData.self, from: data)
    }

    private func uploadData(db: CKDatabase, data: SyncData) async throws {
        let payload = try JSONEncoder().encode(data)
        let record = (try? await db.record(for: Self.recordID)) ?? CKRecord(recordType: Self.recordType, recordID: Self.recordID)
        record[Self.payloadKey] = payload as CKRecordValue
        record[Self.lastUpdatedKey] = data.lastUpdated as CKRecordValue
        _ = try await db.save(record)
    }
}
