import Foundation
import SwiftData

/// Mirrors Android's `data.repository.LogRepository`, including the adaptive
/// daily-target algorithm: base target (AI-computed or Mifflin-St Jeor) +
/// yesterday's over/under-eating "bleedthrough" + today's HealthKit active burn
/// + a slow weight-trend correction, floored at 80% of BMR.
///
/// SwiftData's `mainContext` is main-actor isolated, so this repository is too —
/// matches how the original dispatches Room's suspend DAOs back onto the UI
/// layer's collectors; the underlying disk I/O is still off the render loop.
@MainActor
public final class LogRepository {
    private let modelContainer: ModelContainer
    private let profileStore: UserProfileStore
    private let health: HealthProviding
    private let onDataChanged: @MainActor () -> Void

    private var context: ModelContext { modelContainer.mainContext }
    private static let dayInterval: TimeInterval = 24 * 60 * 60

    public init(
        modelContainer: ModelContainer,
        profileStore: UserProfileStore = .shared,
        health: HealthProviding = NoopHealthProvider(),
        onDataChanged: @escaping @MainActor () -> Void = {}
    ) {
        self.modelContainer = modelContainer
        self.profileStore = profileStore
        self.health = health
        self.onDataChanged = onDataChanged
    }

    private func dayRange(offsetDays: Int) -> (start: Date, end: Date) {
        let start = Calendar.current.startOfDay(
            for: Date().addingTimeInterval(Double(offsetDays) * Self.dayInterval)
        )
        return (start, start.addingTimeInterval(Self.dayInterval))
    }

    // MARK: - Meal log

    public func entries(forDateOffset offsetDays: Int) throws -> [LogEntry] {
        let (start, end) = dayRange(offsetDays: offsetDays)
        return try entries(from: start, to: end)
    }

    public func entries(from start: Date, to end: Date) throws -> [LogEntry] {
        let descriptor = FetchDescriptor<LogEntry>(
            predicate: #Predicate { $0.timestamp >= start && $0.timestamp < end },
            sortBy: [SortDescriptor(\.timestamp, order: .reverse)]
        )
        return try context.fetch(descriptor)
    }

    public func addEntry(_ facts: NutritionFacts) throws {
        context.insert(LogEntry(from: facts))
        try context.save()
        onDataChanged()
    }

    public func updateEntry(_ entry: LogEntry) throws {
        try context.save()
        onDataChanged()
    }

    public func deleteEntry(_ entry: LogEntry) throws {
        context.delete(entry)
        try context.save()
        onDataChanged()
    }

    public func allEntries() throws -> [LogEntry] {
        try context.fetch(FetchDescriptor<LogEntry>())
    }

    public func upsertEntries(_ entries: [LogEntry]) throws {
        for entry in entries { context.insert(entry) }
        try context.save()
    }

    // MARK: - Water log

    public func waterLogs(forDateOffset offsetDays: Int) throws -> [WaterLog] {
        let (start, end) = dayRange(offsetDays: offsetDays)
        let descriptor = FetchDescriptor<WaterLog>(
            predicate: #Predicate { $0.timestamp >= start && $0.timestamp < end },
            sortBy: [SortDescriptor(\.timestamp, order: .reverse)]
        )
        return try context.fetch(descriptor)
    }

    public func addWater(_ amountMl: Int) throws {
        context.insert(WaterLog(amountMl: amountMl))
        try context.save()
        onDataChanged()
    }

    public func removeLastWater() throws {
        var descriptor = FetchDescriptor<WaterLog>(sortBy: [SortDescriptor(\.timestamp, order: .reverse)])
        descriptor.fetchLimit = 1
        guard let latest = try context.fetch(descriptor).first else { return }
        context.delete(latest)
        try context.save()
        onDataChanged()
    }

    public func waterTotal(from start: Date, to end: Date) throws -> Int {
        let descriptor = FetchDescriptor<WaterLog>(
            predicate: #Predicate { $0.timestamp >= start && $0.timestamp < end }
        )
        return try context.fetch(descriptor).reduce(0) { $0 + $1.amountMl }
    }

    // MARK: - Targets (delegated to UserProfileStore — see its doc comment for the formulas)

    public func dailyCalorieTarget() -> Int { profileStore.dailyCalorieTarget() }
    public func bmr() -> Int { Int(profileStore.bmr()) }
    public func goalOffset() -> Int { profileStore.goalOffset() }
    public func macroTargets() -> MacroTargets { profileStore.macroTargets() }
    public func goalIndex() -> Int { profileStore.goalIndex }
    public func isAiComputed() -> Bool { profileStore.aiCalorieTarget > 0 }

    public func syncActiveCalories(_ kcal: Double) {
        profileStore.lastActiveCalories = kcal
        onDataChanged()
    }

    // MARK: - Adaptive daily target

    public func liveTarget() async throws -> Int {
        let hasHealth = await health.hasPermissions()
        let bleedthrough = try await yesterdayBleedthrough()
        let base = baseTarget(hasHealth: hasHealth)

        var activeKcal = hasHealth ? await health.readActiveCalories(offsetDays: 0) : 0
        // Background restriction fallback: if the live read comes back empty but we
        // have a cached value (e.g. from the widget's last successful refresh), use it.
        if hasHealth, activeKcal <= 0.1, profileStore.lastActiveCalories > 0 {
            activeKcal = profileStore.lastActiveCalories
        } else if hasHealth, activeKcal > 0.1 {
            profileStore.lastActiveCalories = activeKcal
        }

        let trend: Int
        if hasHealth {
            let history = await health.readWeightHistory(pastDays: 28)
            trend = trendAdjustment(history)
        } else {
            trend = 0
        }

        return finalTarget(base: base, bleedthrough: bleedthrough, active: activeKcal, trend: trend)
    }

    private func baseTarget(hasHealth: Bool) -> Int {
        guard hasHealth else { return dailyCalorieTarget() }
        return isAiComputed() ? dailyCalorieTarget() : Int(Double(bmr()) * 1.2) + goalOffset()
    }

    private func finalTarget(base: Int, bleedthrough: Int, active: Double, trend: Int) -> Int {
        let total = base + bleedthrough + Int(active.rounded()) + trend
        // Ensure a healthy minimum target (at least 80% of BMR).
        return max(total, Int(Double(bmr()) * 0.8))
    }

    private func trendAdjustment(_ readings: [(Date, Double)]) -> Int {
        guard readings.count >= 2 else { return 0 }
        let sorted = readings.sorted { $0.0 < $1.0 }
        guard let first = sorted.first, let last = sorted.last else { return 0 }
        let daysDiff = last.0.timeIntervalSince(first.0) / Self.dayInterval
        guard daysDiff >= 5 else { return 0 }

        let weeklyRate = (last.1 - first.1) / (daysDiff / 7.0)

        switch goalIndex() {
        case 0: // lose weight
            if weeklyRate < -0.75 { return 200 }
            if weeklyRate > -0.20 { return -200 }
            return 0
        case 2: // build muscle
            if weeklyRate > 0.45 { return -200 }
            if weeklyRate < 0.10 { return 200 }
            return 0
        default:
            return 0
        }
    }

    public func yesterdayBleedthrough() async throws -> Int {
        let (start, end) = dayRange(offsetDays: -1)

        let yesterdayEntries = try entries(from: start, to: end)
        let consumed = yesterdayEntries.reduce(0.0) { $0 + $1.calories }

        let hasHealth = await health.hasPermissions()
        let base = baseTarget(hasHealth: hasHealth)
        let activeYesterday = hasHealth ? await health.readActiveCaloriesRange(start: start, end: end) : 0

        let totalAllowance = Double(base) + activeYesterday
        let rawBalance = totalAllowance - consumed

        // If saved (balance > 0): use only 80% to account for body efficiency.
        // If overspent (balance < 0): carry over 100% to keep the user accountable.
        // Cap at ±500 kcal to keep today's target healthy and achievable.
        let adjustedBalance = rawBalance > 0 ? rawBalance * 0.8 : rawBalance
        return Int(adjustedBalance.rounded()).clamped(to: -500...500)
    }
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}
