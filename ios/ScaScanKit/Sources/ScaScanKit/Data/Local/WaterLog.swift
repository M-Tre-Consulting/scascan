import Foundation
import SwiftData

/// Mirrors Android's `data.local.WaterLog` Room entity (table `water_logs`).
@Model
public final class WaterLog {
    /// Stable identity used to tag the matching HealthKit `dietaryWater`
    /// sample — see `HealthKitManager.writeWater`.
    public var id: UUID
    public var timestamp: Date
    /// e.g. 250
    public var amountMl: Int

    public init(id: UUID = UUID(), timestamp: Date = .now, amountMl: Int) {
        self.id = id
        self.timestamp = timestamp
        self.amountMl = amountMl
    }
}
