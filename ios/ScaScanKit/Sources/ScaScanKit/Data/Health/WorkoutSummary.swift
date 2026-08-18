import Foundation

/// A single Apple Watch / Fitness app workout session read back from
/// HealthKit, shown in the Log tab so the user can see (and trust) that
/// their training sessions are actually feeding into today's calorie math —
/// `activeKcal` on `LogViewState.AdaptiveState.Active` already includes
/// these via HealthKit's aggregate `activeEnergyBurned` sum, this is purely
/// the human-readable breakdown of *why* that number is what it is.
public struct WorkoutSummary: Sendable, Equatable, Identifiable {
    public var id: String
    public var activityName: String
    public var start: Date
    public var duration: TimeInterval
    public var kcal: Double

    public init(id: String, activityName: String, start: Date, duration: TimeInterval, kcal: Double) {
        self.id = id
        self.activityName = activityName
        self.start = start
        self.duration = duration
        self.kcal = kcal
    }
}
