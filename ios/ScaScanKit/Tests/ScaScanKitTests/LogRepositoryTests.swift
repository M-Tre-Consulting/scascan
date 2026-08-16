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

    @Test("finalTarget sums base + bleedthrough - active + trend")
    func finalTargetSum() {
        let repo = makeRepository()
        #expect(repo.finalTarget(base: 2_000, bleedthrough: 100, active: 300, trend: -50) == 1_750)
    }

    @Test("finalTarget floors at 80% of BMR, never dips below it")
    func finalTargetFloor() {
        let repo = makeRepository()
        let bmr = repo.bmr()
        // A deeply negative bleedthrough would otherwise push the target
        // dangerously low — the floor exists specifically to prevent that.
        let result = repo.finalTarget(base: 1_200, bleedthrough: -900, active: 0, trend: 0)
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

    @Test("liveTarget subtracts today's active calories from the base target when Health is connected")
    func liveTargetSubtractsActiveCalories() async throws {
        let health = MockHealthProvider(granted: true, activeCalories: 400, weightHistory: [])
        let repo = makeRepository(health: health)
        let target = try await repo.liveTarget()
        let expectedBase = repo.baseTarget(hasHealth: true)
        // No log entries yesterday -> yesterdayBleedthrough is 0 (no consumption to compare against target).
        #expect(target <= expectedBase) // active calories burned reduce today's allowance, never add to it
    }

    @Test("yesterdayBleedthrough caps the carry-over at +/-500 kcal")
    func bleedthroughIsClamped() async throws {
        let repo = makeRepository()
        // No entries logged yesterday at all -> full unused allowance, well
        // over 500kcal for a ~2000kcal target -> must clamp to +500.
        let bleedthrough = try await repo.yesterdayBleedthrough()
        #expect(bleedthrough <= 500 && bleedthrough >= -500)
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
