import Foundation

/// Abstraction over the platform health store — mirrors what Android's
/// `HealthConnectManager` exposes to `LogRepository`. The real implementation
/// (`HealthKitManager`, wrapping HealthKit) lands in Phase 4 of the port; until
/// then `NoopHealthProvider` keeps every consumer compiling and behaving exactly
/// like Android does when Health Connect is unavailable or unauthorized.
///
/// `@MainActor`: every conformer (`HealthKitManager`, `LogRepository` itself)
/// already lives on the main actor, and `writeDietaryEntry`/`writeWater` take
/// SwiftData `@Model` reference types, which aren't `Sendable` — pinning the
/// whole protocol to the main actor makes calls through it same-actor hops
/// instead of cross-isolation sends, so passing those models through needs
/// no unsafe opt-outs.
@MainActor
public protocol HealthProviding: Sendable {
    func hasPermissions() async -> Bool
    func readActiveCalories(offsetDays: Int) async -> Double
    func readActiveCaloriesRange(start: Date, end: Date) async -> Double
    /// (date, weightKg) pairs, ascending by date.
    func readWeightHistory(pastDays: Int) async -> [(Date, Double)]

    // MARK: - Write-back (best-effort; mirrors Android writing nutrition/water
    // into Health Connect so other apps — and Health itself — see them too)

    func writeDietaryEntry(_ entry: LogEntry) async
    func deleteDietaryEntry(id: UUID) async
    func writeWater(_ log: WaterLog) async
    func deleteWater(id: UUID) async

    // MARK: - Workouts (Apple Watch / Fitness app sessions, read-only)

    func readWorkoutsToday() async -> [WorkoutSummary]
}

/// Default no-ops for the write-back and workout surface, so conformers that
/// predate this (like `NoopHealthProvider` and test mocks) don't need to
/// implement methods they have no use for.
public extension HealthProviding {
    func writeDietaryEntry(_ entry: LogEntry) async {}
    func deleteDietaryEntry(id: UUID) async {}
    func writeWater(_ log: WaterLog) async {}
    func deleteWater(id: UUID) async {}
    func readWorkoutsToday() async -> [WorkoutSummary] { [] }
}

public struct NoopHealthProvider: HealthProviding {
    public init() {}
    public func hasPermissions() async -> Bool { false }
    public func readActiveCalories(offsetDays: Int) async -> Double { 0 }
    public func readActiveCaloriesRange(start: Date, end: Date) async -> Double { 0 }
    public func readWeightHistory(pastDays: Int) async -> [(Date, Double)] { [] }
}
