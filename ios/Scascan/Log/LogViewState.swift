import Foundation
import Observation
import ScaScanKit

/// Mirrors Android's `LogViewModel`. HealthKit integration lands in Phase 4 —
/// `adaptiveState` stays `.disconnected` until then, exactly matching how the
/// Android screen behaves before the user connects Health Connect.
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

    /// Base target + today's active burn + weight-trend correction + yesterday's
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

    init(repository: LogRepository) {
        self.repository = repository
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
        Task { await refreshTargets() }
    }

    private func refreshTargets() async {
        // hasHealth is always false until Phase 4 wires a real HealthProviding.
        let bleedthrough = isToday ? ((try? await repository.yesterdayBleedthrough()) ?? 0) : 0
        targetInfo = TargetInfo(
            caloriesKcal: repository.baseTarget(hasHealth: false),
            bmrKcal: repository.bmr(),
            goalOffsetKcal: repository.goalOffset(),
            macros: repository.macroTargets(),
            goalIndex: repository.goalIndex(),
            isAiComputed: repository.isAiComputed(),
            bleedthroughKcal: bleedthrough
        )
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
