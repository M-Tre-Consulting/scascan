import Foundation
import Observation
import ScaScanKit

/// Mirrors Android's `LogViewModel`, including the Health Connect ->
/// HealthKit-backed adaptive-target card.
///
/// One platform-shaped simplification: Android distinguishes "Health Connect
/// not installed" from "installed but not authorized" (`HcUnavailable` vs
/// `HcDisconnected`) because Health Connect can be a missing separate app.
/// HealthKit ships with iOS itself, so that first case can't happen here —
/// the two collapse into a single `.disconnected`.
@MainActor
@Observable
final class LogViewState {
    enum AdaptiveState: Equatable {
        case disconnected
        case active(Active)

        struct Active: Equatable {
            var steps: Int
            var activeKcal: Double
            var trendAdjustment: Int
            var trendStatus: TrendStatus
            var weeklyRateKgPerWeek: Double?
        }

        enum TrendStatus: Equatable {
            case noData, onTrack, tooFast, tooSlow
        }
    }

    struct TargetInfo: Equatable {
        var caloriesKcal = 2_000
        var bmrKcal = 1_500
        var goalOffsetKcal = 0
        var macros = MacroTargets(proteinG: 0, carbsG: 0, fatG: 0)
        var goalIndex = 1
        var isAiComputed = false
        var bleedthroughKcal = 0
    }

    private(set) var dateOffset = 0
    private(set) var entries: [LogEntry] = []
    private(set) var water: [WaterLog] = []
    private(set) var adaptiveState: AdaptiveState = .disconnected
    private(set) var targetInfo = TargetInfo()
    /// Today's Apple Watch / Fitness app sessions — empty on past days (and
    /// when Health isn't connected), since it's only meaningful as "what's
    /// feeding into today's live target".
    private(set) var workoutsToday: [WorkoutSummary] = []

    /// Base target - today's active burn + weight-trend correction + yesterday's
    /// bleedthrough — the number actually shown as "today's target".
    var liveTarget: Int {
        guard isToday else { return targetInfo.caloriesKcal }
        let active: Double
        let trend: Int
        if case .active(let a) = adaptiveState {
            active = a.activeKcal
            trend = a.trendAdjustment
        } else {
            active = 0
            trend = 0
        }
        return repository.finalTarget(
            base: targetInfo.caloriesKcal,
            bleedthrough: targetInfo.bleedthroughKcal,
            active: active,
            trend: trend
        )
    }

    private let repository: LogRepository
    private let health: HealthKitManager

    init(repository: LogRepository, health: HealthKitManager) {
        self.repository = repository
        self.health = health
        reload()
    }

    var isToday: Bool { dateOffset == 0 }

    var selectedDateLabel: String {
        switch dateOffset {
        case 0: return "Today"
        case -1: return "Yesterday"
        default:
            let date = Calendar.current.date(byAdding: .day, value: dateOffset, to: Date()) ?? Date()
            return date.formatted(.dateTime.month(.abbreviated).day())
        }
    }

    func goToPreviousDay() {
        dateOffset -= 1
        reload()
    }

    func goToNextDay() {
        guard dateOffset < 0 else { return }
        dateOffset += 1
        reload()
    }

    func reload() {
        entries = (try? repository.entries(forDateOffset: dateOffset)) ?? []
        water = (try? repository.waterLogs(forDateOffset: dateOffset)) ?? []
        Task {
            await loadHealthData()
            await refreshTargets()
        }
    }

    private func refreshTargets() async {
        let hasHealth = health.isAvailable ? await health.hasPermissions() : false
        let bleedthrough = isToday ? ((try? await repository.yesterdayBleedthrough()) ?? 0) : 0
        targetInfo = TargetInfo(
            caloriesKcal: repository.baseTarget(hasHealth: hasHealth),
            bmrKcal: repository.bmr(),
            goalOffsetKcal: repository.goalOffset(),
            macros: repository.macroTargets(),
            goalIndex: repository.goalIndex(),
            isAiComputed: repository.isAiComputed(),
            bleedthroughKcal: bleedthrough
        )
    }

    func loadHealthData() async {
        guard health.isAvailable else { adaptiveState = .disconnected; workoutsToday = []; return }
        guard await health.hasPermissions() else { adaptiveState = .disconnected; workoutsToday = []; return }

        let steps = await health.readSteps(offsetDays: dateOffset)
        // Raw reading is what gets cached below (so the background-refresh
        // fallback cache always holds a real value, never the substitute);
        // `effectiveActiveCalories` is what's actually shown/used for the
        // target — see its doc comment for the "Watch wasn't worn" rule.
        let rawActiveKcal = await health.readActiveCalories(offsetDays: dateOffset)
        let activeKcal = repository.effectiveActiveCalories(rawActiveKcal)
        workoutsToday = isToday ? await health.readWorkoutsToday() : []

        // Weight trend is only meaningful for today's adaptive goal.
        let trend: TrendResult
        if isToday {
            let history = await health.readWeightHistory(pastDays: 28)
            trend = computeTrend(history)
        } else {
            trend = TrendResult(adjustment: 0, status: .noData, weeklyRate: nil)
        }

        adaptiveState = .active(.init(
            steps: steps,
            activeKcal: activeKcal,
            trendAdjustment: trend.adjustment,
            trendStatus: trend.status,
            weeklyRateKgPerWeek: trend.weeklyRate
        ))

        if isToday, rawActiveKcal > 0 {
            repository.syncActiveCalories(rawActiveKcal)
        }
    }

    private struct TrendResult {
        var adjustment: Int
        var status: AdaptiveState.TrendStatus
        var weeklyRate: Double?
    }

    /// Computes a stepped ±200 kcal/day correction from weight readings.
    /// Goal rates (kg/wk): Lose -0.5 (fast <-0.75, slow >-0.20) · Maintain 0 ·
    /// Build +0.25 (fast >0.45, slow <0.10). Mirrors LogRepository's identical
    /// server-side copy of this formula used for the bleedthrough calc.
    private func computeTrend(_ readings: [(Date, Double)]) -> TrendResult {
        guard readings.count >= 2 else { return TrendResult(adjustment: 0, status: .noData, weeklyRate: nil) }
        let sorted = readings.sorted { $0.0 < $1.0 }
        guard let first = sorted.first, let last = sorted.last else {
            return TrendResult(adjustment: 0, status: .noData, weeklyRate: nil)
        }
        let daysDiff = last.0.timeIntervalSince(first.0) / 86_400
        guard daysDiff >= 5 else { return TrendResult(adjustment: 0, status: .noData, weeklyRate: nil) }

        let weeklyRate = (last.1 - first.1) / (daysDiff / 7.0)

        switch repository.goalIndex() {
        case 0: // lose weight
            if weeklyRate < -0.75 { return TrendResult(adjustment: 200, status: .tooFast, weeklyRate: weeklyRate) }
            if weeklyRate > -0.20 { return TrendResult(adjustment: -200, status: .tooSlow, weeklyRate: weeklyRate) }
            return TrendResult(adjustment: 0, status: .onTrack, weeklyRate: weeklyRate)
        case 2: // build muscle
            if weeklyRate > 0.45 { return TrendResult(adjustment: -200, status: .tooFast, weeklyRate: weeklyRate) }
            if weeklyRate < 0.10 { return TrendResult(adjustment: 200, status: .tooSlow, weeklyRate: weeklyRate) }
            return TrendResult(adjustment: 0, status: .onTrack, weeklyRate: weeklyRate)
        default:
            return TrendResult(adjustment: 0, status: .onTrack, weeklyRate: weeklyRate)
        }
    }

    func addWater(_ ml: Int) {
        try? repository.addWater(ml)
        reload()
    }

    func removeLastWater() {
        try? repository.removeLastWater()
        reload()
    }

    func deleteEntry(_ entry: LogEntry) {
        try? repository.deleteEntry(entry)
        reload()
    }
}
