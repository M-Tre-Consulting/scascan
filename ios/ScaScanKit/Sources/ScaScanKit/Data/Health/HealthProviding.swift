import Foundation

/// Abstraction over the platform health store — mirrors what Android's
/// `HealthConnectManager` exposes to `LogRepository`. The real implementation
/// (`HealthKitManager`, wrapping HealthKit) lands in Phase 4 of the port; until
/// then `NoopHealthProvider` keeps every consumer compiling and behaving exactly
/// like Android does when Health Connect is unavailable or unauthorized.
public protocol HealthProviding: Sendable {
    func hasPermissions() async -> Bool
    func readActiveCalories(offsetDays: Int) async -> Double
    func readActiveCaloriesRange(start: Date, end: Date) async -> Double
    /// (date, weightKg) pairs, ascending by date.
    func readWeightHistory(pastDays: Int) async -> [(Date, Double)]
}

public struct NoopHealthProvider: HealthProviding {
    public init() {}
    public func hasPermissions() async -> Bool { false }
    public func readActiveCalories(offsetDays: Int) async -> Double { 0 }
    public func readActiveCaloriesRange(start: Date, end: Date) async -> Double { 0 }
    public func readWeightHistory(pastDays: Int) async -> [(Date, Double)] { [] }
}
