import Testing
import Foundation
import SwiftData
@testable import ScaScanKit

/// A scriptable `HealthProviding` stand-in, so the adaptive-target algorithm
/// can be exercised deterministically without touching real HealthKit.
private struct MockHealthProvider: HealthProviding {
    var granted = true
    var activeCalories: Double = 0
    var weightHistory: [(Date, Double)] = []

    func hasPermissions() async -> Bool { granted }
    func readActiveCalories(offsetDays: Int) async -> Double { activeCalories }
    func readActiveCaloriesRange(start: Date, end: Date) async -> Double { activeCalories }
    func readWeightHistory(pastDays: Int) async -> [(Date, Double)] { weightHistory }
}

@MainActor
@Suite("LogRepository — adaptive target algorithm")
struct LogRepositoryTests {
    private func makeRepository(health: HealthProviding = NoopHealthProvider()) -> LogRepository {
        let container = ScaScanSchema.makeContainer(inMemory: true)
        let suiteName = "test.\(UUID().uuidString)"
        let profileStore = UserProfileStore(defaults: UserDefaults(suiteName: suiteName)!)
        profileStore.age = 28
        profileStore.heightCm = 180
        profileStore.weightKg = 85
        profileStore.isMale = true
        profileStore.activityIndex = 2
        profileStore.goalIndex = 1 // maintain, offset 0 — keeps target math simple
        return LogRepository(modelContainer: container, profileStore: profileStore, health: health, onDataChanged: {})
    }

    @Test("finalTarget sums base + bleedthrough + trend")
    func finalTargetSum() {
        let repo = makeRepository()
        #expect(repo.finalTarget(base: 2_000, bleedthrough: 100, trend: -50) == 2_050)
    }

    @Test("finalTarget floors at 80% of BMR, never dips below it")
    func finalTargetFloor() {
        let repo = makeRepository()
        let bmr = repo.bmr()
        // A deeply negative bleedthrough would otherwise push the target
        // dangerously low — the floor exists specifically to prevent that.
        let result = repo.finalTarget(base: 1_200, bleedthrough: -900, trend: 0)
        #expect(result == Int(Double(bmr) * 0.8))
    }

    @Test("baseTarget uses the profile/AI target when Health is unavailable")
    func baseTargetWithoutHealth() {
        let repo = makeRepository()
        #expect(repo.baseTarget(hasHealth: false) == repo.dailyCalorieTarget())
    }

    @Test("baseTarget uses 1.2x BMR + goal offset when Health is connected and no AI target exists")
    func baseTargetWithHealthNoAI() {
        let repo = makeRepository()
        let expected = Int(Double(repo.bmr()) * 1.2) + repo.goalOffset()
        #expect(repo.baseTarget(hasHealth: true) == expected)
    }

    @Test("Adding and removing water updates today's total")
    func waterTracking() throws {
        let repo = makeRepository()
        try repo.addWater(250)
        try repo.addWater(100)
        #expect(try repo.waterLogs(forDateOffset: 0).reduce(0) { $0 + $1.amountMl } == 350)

        try repo.removeLastWater()
        #expect(try repo.waterLogs(forDateOffset: 0).reduce(0) { $0 + $1.amountMl } == 250)
    }

    @Test("Logging an entry round-trips through the store")
    func logEntryRoundTrip() throws {
        let repo = makeRepository()
        let facts = NutritionFacts(
            foodName: "Chicken breast", servingSize: "100g", calories: 165,
            protein: 31, carbohydrates: 0, fat: 3.6, fiber: 0, sugar: 0, sodium: 74
        )
        try repo.addEntry(facts)
        let entries = try repo.entries(forDateOffset: 0)
        #expect(entries.count == 1)
        #expect(entries.first?.foodName == "Chicken breast")
    }

    @Test("liveTarget ignores today's active calories entirely — the burn belongs to the evening recap")
    func liveTargetIgnoresActiveCalories() async throws {
        let base = MockHealthProvider(granted: true, activeCalories: 0, weightHistory: [])
        let busy = MockHealthProvider(granted: true, activeCalories: 900, weightHistory: [])
        // Yesterday's carry-over does legitimately depend on yesterday's burn,
        // and the mock reports the same figure for any range — so compare with
        // yesterday's contribution held constant by having nothing logged then.
        let quietTarget = try await makeRepository(health: base).liveTarget()
        let busyRepo = makeRepository(health: busy)
        let busyTarget = try await busyRepo.liveTarget()
        // Both days' carry-over clamps to +500 (nothing eaten yesterday either
        // way), so any difference between these two could only come from today's
        // burn leaking into the target.
        #expect(quietTarget == busyTarget)
    }

    @Test("dailyRecap settles the burn and the carry-over against intake, not against the target")
    func dailyRecapLedger() async throws {
        let health = MockHealthProvider(granted: true, activeCalories: 400, weightHistory: [])
        let repo = makeRepository(health: health)
        let facts = NutritionFacts(
            foodName: "Pasta", servingSize: "300g", calories: 1_500,
            protein: 50, carbohydrates: 200, fat: 30, fiber: 8, sugar: 10, sodium: 400
        )
        try repo.addEntry(facts)

        let recap = try await repo.dailyRecap(forDateOffset: 0)
        #expect(recap.meals.count == 1)
        #expect(recap.consumedKcal == 1_500)
        #expect(recap.burnedKcal == 400)
        // Nothing logged yesterday -> the full unused allowance carries over,
        // clamped to +500.
        #expect(recap.carryOverKcal == 500)
        #expect(recap.netKcal == 1_500 - 400 - 500)
        // The target it's measured against carries neither of those deductions.
        #expect(recap.targetKcal == repo.finalTarget(base: repo.baseTarget(hasHealth: true), bleedthrough: 0, trend: 0))
    }

    @Test("dailyRecap's verdict flags over-eating, under-eating, and a day on target")
    func dailyRecapVerdicts() {
        func recap(net: Double, target: Int) -> DailyRecap {
            DailyRecap(
                dayStart: .now, offsetDays: 0, meals: [], consumedKcal: net, burnedKcal: 0,
                carryOverKcal: 0, targetKcal: target, trendKcal: 0, waterMl: 0,
                waterTargetMl: 2_000, proteinG: 0, carbsG: 0, fatG: 0
            )
        }
        #expect(recap(net: 2_000, target: 2_000).verdict == .onTarget)
        // Inside the 100 kcal grace band above the target.
        #expect(recap(net: 2_080, target: 2_000).verdict == .onTarget)
        #expect(recap(net: 2_400, target: 2_000).verdict == .over)
        // Below 75% of the target reads as under-eating, not as a win.
        #expect(recap(net: 1_400, target: 2_000).verdict == .under)
    }

    @Test("yesterdayBleedthrough caps the carry-over at +/-500 kcal")
    func bleedthroughIsClamped() async throws {
        let repo = makeRepository()
        // No entries logged yesterday at all -> full unused allowance, well
        // over 500kcal for a ~2000kcal target -> must clamp to +500.
        let bleedthrough = try await repo.yesterdayBleedthrough()
        #expect(bleedthrough <= 500 && bleedthrough >= -500)
    }

    @Test("liveTarget reconstructs the adaptive target from the shared cache when this process has no direct Health access (the widget's situation)")
    func liveTargetUsesSharedCacheWithoutDirectHealthAccess() async throws {
        let container = ScaScanSchema.makeContainer(inMemory: true)
        let suiteName = "test.\(UUID().uuidString)"
        let profileStore = UserProfileStore(defaults: UserDefaults(suiteName: suiteName)!)
        profileStore.age = 28
        profileStore.heightCm = 180
        profileStore.weightKg = 85
        profileStore.isMale = true
        profileStore.activityIndex = 2
        profileStore.goalIndex = 1

        // Simulates the app itself having confirmed Health authorization and
        // cached today's active-calorie reading, then a second, HealthKit-less
        // reader (the widget) coming along afterwards.
        profileStore.isHealthConnected = true
        profileStore.lastActiveCalories = 300

        let repo = LogRepository(modelContainer: container, profileStore: profileStore, health: NoopHealthProvider(), onDataChanged: {})
        let target = try await repo.liveTarget()
        let adaptiveBase = repo.baseTarget(hasHealth: true)
        let bleedthrough = try await repo.yesterdayBleedthrough()
        // No trend (NoopHealthProvider has no weight history), and today's burn
        // never touches the target — so yesterday's bleedthrough is the only
        // adjustment left. What the shared flag buys here is the *adaptive* base
        // (1.2x BMR) instead of the plain profile target.
        #expect(target == adaptiveBase + bleedthrough)
        #expect(adaptiveBase != repo.dailyCalorieTarget())
    }

    @Test("yesterdayBleedthrough reuses the cached active-calories figure when this process has no direct Health access, as long as it's still tagged as \"yesterday\"")
    func bleedthroughUsesSharedYesterdayCacheWithoutDirectHealthAccess() async throws {
        let container = ScaScanSchema.makeContainer(inMemory: true)
        let suiteName = "test.\(UUID().uuidString)"
        let profileStore = UserProfileStore(defaults: UserDefaults(suiteName: suiteName)!)
        profileStore.age = 28
        profileStore.heightCm = 180
        profileStore.weightKg = 85
        profileStore.isMale = true
        profileStore.activityIndex = 2
        profileStore.goalIndex = 1
        profileStore.isHealthConnected = true

        let repo = LogRepository(modelContainer: container, profileStore: profileStore, health: NoopHealthProvider(), onDataChanged: {})
        let yesterdayStart = Calendar.current.startOfDay(for: Date().addingTimeInterval(-86_400))

        // Logged just under yesterday's base allowance, so the balance stays
        // comfortably unclamped either way — isolating the cache's effect.
        let base = repo.baseTarget(hasHealth: true)
        try repo.upsertEntries([
            LogEntry(
                timestamp: yesterdayStart.addingTimeInterval(12 * 3_600),
                foodName: "Test meal", servingSize: "1", calories: Double(base) - 8,
                protein: 0, carbohydrates: 0, fat: 0, fiber: 0, sugar: 0, sodium: 0
            )
        ])

        // Tagged as "yesterday" relative to now -> should be reused.
        profileStore.lastYesterdayActiveCalories = 400
        profileStore.lastYesterdayActiveCaloriesDayStart = yesterdayStart.timeIntervalSince1970
        let withFreshCache = try await repo.yesterdayBleedthrough()

        // Tagged as some day well before yesterday -> stale, must be ignored
        // (falls back to treating yesterday's active burn as 0).
        profileStore.lastYesterdayActiveCaloriesDayStart = yesterdayStart.addingTimeInterval(-5 * 86_400).timeIntervalSince1970
        let withStaleCache = try await repo.yesterdayBleedthrough()

        // Honoring the cached 400kcal burn raises what yesterday could afford to
        // eat, so it should yield a meaningfully *higher* carry-over than
        // ignoring it does.
        #expect(withFreshCache > withStaleCache)
    }

    @Test("effectiveActiveCalories substitutes the configured base when the reading looks like a missed Watch day")
    func effectiveActiveCaloriesFallsBackBelowThreshold() {
        let repo = makeRepository()
        // Under 100kcal for a whole day reads as "Watch wasn't worn" -> the
        // user's configured flat fallback (500kcal by default) is used instead.
        #expect(repo.effectiveActiveCalories(30) == 500)
        #expect(repo.effectiveActiveCalories(0) == 500)
        // At/above the threshold, the real reading passes through untouched.
        #expect(repo.effectiveActiveCalories(150) == 150)
        #expect(repo.effectiveActiveCalories(600) == 600)
    }
}
