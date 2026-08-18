import Foundation
import SwiftData

/// Mirrors Android's `data.local.WaterLog` Room entity (table `water_logs`).
@Model
public final class WaterLog {
    /// Stable identity used to tag the matching HealthKit `dietaryWater`
    /// sample — see `HealthKitManager.writeWater`. The `= UUID()` default
    /// must live here (not just on `init` below) so SwiftData's lightweight
    /// migration has a default to backfill this column with on rows that
    /// already exist from before this field existed — see `LogEntry.id`'s
    /// doc comment for the full explanation.
    public var id: UUID = UUID()
    public var timestamp: Date
    /// e.g. 250
    public var amountMl: Int

    public init(id: UUID = UUID(), timestamp: Date = .now, amountMl: Int) {
        self.id = id
        self.timestamp = timestamp
        self.amountMl = amountMl
    }
}
