import Foundation
import SwiftData

/// Mirrors Android's `data.local.WaterLog` Room entity (table `water_logs`).
@Model
public final class WaterLog {
    public var timestamp: Date
    /// e.g. 250
    public var amountMl: Int

    public init(timestamp: Date = .now, amountMl: Int) {
        self.timestamp = timestamp
        self.amountMl = amountMl
    }
}
