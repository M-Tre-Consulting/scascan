import Foundation
import SwiftData

/// Mirrors Android's `data.repository.LogRepository`, including the adaptive
/// daily-target algorithm: base target (AI-computed or Mifflin-St Jeor) +
/// yesterday's over/under-eating "bleedthrough" + a slow weight-trend
/// correction, floored at 80% of BMR.
///
/// Note what the target deliberately does *not* include: today's active burn.
/// A target that grows every time the Watch syncs another walk is a moving
/// goalpost — you can never tell whether you're doing well, because the line
/// keeps moving. Instead the day's burn is settled once, in the evening recap
/// (`dailyRecap(forDateOffset:)`), as a deduction from what was eaten. Same
/// arithmetic, but it reads as a result rather than a shifting allowance. See
/// `DailyRecap`.
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

    @discardableResult
    public func addEntry(_ facts: NutritionFacts) throws -> LogEntry {
        let entry = LogEntry(from: facts)
        context.insert(entry)
        try context.save()
        onDataChanged()
        Task { await health.writeDietaryEntry(entry) }
        return entry
    }

    public func updateEntry(_ entry: LogEntry) throws {
        try context.save()
        onDataChanged()
        // HealthKit has no "update in place" — replace the old correlation
        // (found via the entry's own stable id) with a freshly written one.
        let entryID = entry.id
        Task {
            await health.deleteDietaryEntry(id: entryID)
            await health.writeDietaryEntry(entry)
        }
    }

    public func deleteEntry(_ entry: LogEntry) throws {
        // Capture before `context.delete` — a SwiftData model can fault once
        // it's been deleted from its context, so read what's needed first.
        let entryID = entry.id
        context.delete(entry)
        try context.save()
        onDataChanged()
        Task { await health.deleteDietaryEntry(id: entryID) }
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
        let log = WaterLog(amountMl: amountMl)
        context.insert(log)
        try context.save()
        onDataChanged()
        Task { await health.writeWater(log) }
    }

    public func removeLastWater() throws {
        var descriptor = FetchDescriptor<WaterLog>(sortBy: [SortDescriptor(\.timestamp, order: .reverse)])
        descriptor.fetchLimit = 1
        guard let latest = try context.fetch(descriptor).first else { return }
        let logID = latest.id
        context.delete(latest)
        try context.save()
        onDataChanged()
        Task { await health.deleteWater(id: logID) }
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

    /// Called once the app confirms Health is authorized, so out-of-process
    /// readers with no HealthKit access of their own (the widget extension)
    /// can still reconstruct an adaptive target instead of the plain base —
    /// see `UserProfileStore.isHealthConnected`.
    public func markHealthConnected() {
        profileStore.isHealthConnected = true
    }

    // MARK: - Adaptive daily target

    public func liveTarget() async throws -> Int {
        let liveHealth = await health.hasPermissions()
        // The widget extension has no HealthKit access of its own (it runs on
        // `NoopHealthProvider`, so `liveHealth` is always false there) — fall
        // back to the shared flag the app itself set once it confirmed
        // authorization, so the widget still computes the adaptive target
        // instead of the plain, unadjusted base.
        let hasHealth = liveHealth || profileStore.isHealthConnected
        let bleedthrough = try await yesterdayBleedthrough()
        let base = baseTarget(hasHealth: hasHealth)
        let trend = hasHealth ? trendAdjustment(await health.readWeightHistory(pastDays: 28)) : 0

        return finalTarget(base: base, bleedthrough: bleedthrough, trend: trend)
    }

    public func baseTarget(hasHealth: Bool) -> Int {
        guard hasHealth else { return dailyCalorieTarget() }
        return isAiComputed() ? dailyCalorieTarget() : Int(Double(bmr()) * 1.2) + goalOffset()
    }

    /// The number shown as "today's target" all day long. Intentionally free of
    /// today's active burn — that lands in the evening recap instead, see this
    /// type's doc comment.
    public func finalTarget(base: Int, bleedthrough: Int, trend: Int) -> Int {
        // Ensure a healthy minimum target (at least 80% of BMR).
        max(base + bleedthrough + trend, Int(Double(bmr()) * 0.8))
    }

    /// Below `activeCaloriesWornThreshold` kcal for a whole day is treated as
    /// "the Apple Watch wasn't worn that day" rather than "a genuinely very
    /// low-activity day" — a worn Watch/iPhone almost always accumulates well
    /// over 100kcal of background + incidental activity alone, so a reading
    /// under that is far more likely a missing device than real behavior.
    /// In that case, the real (misleadingly low) reading is swapped for the
    /// user's configured flat estimate (Settings ▸ "Fitness base fallback",
    /// default 500kcal) instead of quietly undercounting the day's burn.
    /// Used both for today's live target and for yesterday's bleedthrough,
    /// since both represent "how much did Health report for that day".
    private static let activeCaloriesWornThreshold = 100.0

    /// Public: `LogViewState` (the app target) reads its own active-calories
    /// value directly from `HealthProviding` for the breakdown UI rather than
    /// through `liveTarget()` below, so it needs this same adjustment applied
    /// to what it displays — otherwise the visible "Activity today" number
    /// and the actual target it feeds into would silently disagree.
    public func effectiveActiveCalories(_ raw: Double) -> Double {
        raw < Self.activeCaloriesWornThreshold ? Double(profileStore.fitnessBaseFallbackKcal) : raw
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
        try await carryOverBalance(intoDayStarting: dayRange(offsetDays: 0).start)
    }

    /// The balance the day *before* `dayStart` left behind: its whole allowance
    /// (base target plus whatever activity it credited back) minus what was
    /// actually eaten.
    public func carryOverBalance(intoDayStarting dayStart: Date) async throws -> Int {
        let start = dayStart.addingTimeInterval(-Self.dayInterval)
        let consumed = try entries(from: start, to: dayStart).reduce(0.0) { $0 + $1.calories }

        let hasHealth = await health.hasPermissions() || profileStore.isHealthConnected
        let base = baseTarget(hasHealth: hasHealth)
        let active = await activeCalories(forDayStarting: start)

        // Same convention as the recap: a day's measured burn is part of what
        // that day could afford to eat.
        let rawBalance = (Double(base) + active) - consumed

        // If saved (balance > 0): use only 80% to account for body efficiency.
        // If overspent (balance < 0): carry over 100% to keep the user accountable.
        // Cap at ±500 kcal to keep today's target healthy and achievable.
        let adjustedBalance = rawBalance > 0 ? rawBalance * 0.8 : rawBalance
        return Int(adjustedBalance.rounded()).clamped(to: -500...500)
    }

    /// A day's active-energy burn, already run through the "Watch wasn't worn"
    /// substitution, with the shared caches standing in for processes that
    /// can't reach HealthKit themselves (the widget) — see
    /// `UserProfileStore.lastActiveCalories` / `lastYesterdayActiveCalories`.
    private func activeCalories(forDayStarting start: Date) async -> Double {
        let end = start.addingTimeInterval(Self.dayInterval)
        let todayStart = dayRange(offsetDays: 0).start
        let yesterdayStart = dayRange(offsetDays: -1).start

        if await health.hasPermissions() {
            let value = effectiveActiveCalories(await health.readActiveCaloriesRange(start: start, end: end))
            if start == yesterdayStart {
                // Cache the figure (and which day it belongs to) so an
                // out-of-process reader with no Health access of its own can
                // reuse it — it feeds the widget's carry-over.
                profileStore.lastYesterdayActiveCalories = value
                profileStore.lastYesterdayActiveCaloriesDayStart = start.timeIntervalSince1970
            }
            return value
        }

        guard profileStore.isHealthConnected else { return 0 }
        if start == todayStart, profileStore.lastActiveCalories > 0 {
            return effectiveActiveCalories(profileStore.lastActiveCalories)
        }
        // Only trust the cached "yesterday" figure while it still refers to the
        // same calendar day as yesterday from here.
        if start == yesterdayStart,
           profileStore.lastYesterdayActiveCaloriesDayStart == start.timeIntervalSince1970 {
            return profileStore.lastYesterdayActiveCalories
        }
        return 0
    }

    // MARK: - Evening recap

    /// Closes out a day's books: everything eaten, the water logged, and the
    /// two deductions (activity burned, the previous day's balance) that turn
    /// gross intake into what the day actually cost. See `DailyRecap`.
    public func dailyRecap(forDateOffset offsetDays: Int) async throws -> DailyRecap {
        let (start, end) = dayRange(offsetDays: offsetDays)
        let dayEntries = try entries(from: start, to: end)

        let hasHealth = await health.hasPermissions() || profileStore.isHealthConnected
        let base = baseTarget(hasHealth: hasHealth)
        // A past day's weight trend isn't recoverable — the correction is
        // computed from the *current* 28-day window — so it only applies to
        // today, where it's the same number the Log tab is showing.
        let trend: Int
        if offsetDays == 0, hasHealth {
            trend = trendAdjustment(await health.readWeightHistory(pastDays: 28))
        } else {
            trend = 0
        }

        return DailyRecap(
            dayStart: start,
            offsetDays: offsetDays,
            meals: dayEntries
                .sorted { $0.timestamp < $1.timestamp }
                .map {
                    DailyRecap.Meal(
                        id: $0.id, name: $0.foodName, servingSize: $0.servingSize,
                        kcal: $0.calories, loggedAt: $0.timestamp
                    )
                },
            consumedKcal: dayEntries.reduce(0.0) { $0 + $1.calories },
            burnedKcal: await activeCalories(forDayStarting: start),
            carryOverKcal: try await carryOverBalance(intoDayStarting: start),
            targetKcal: finalTarget(base: base, bleedthrough: 0, trend: trend),
            trendKcal: trend,
            waterMl: try waterTotal(from: start, to: end),
            waterTargetMl: profileStore.waterTargetMl,
            proteinG: dayEntries.reduce(0.0) { $0 + $1.protein },
            carbsG: dayEntries.reduce(0.0) { $0 + $1.carbohydrates },
            fatG: dayEntries.reduce(0.0) { $0 + $1.fat }
        )
    }
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}
