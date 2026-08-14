import Testing
import Foundation
@testable import ScaScanKit

/// Pins down the exact Mifflin-St Jeor / TDEE / macro-ratio constants that
/// must stay byte-for-byte identical to the Android original — this file is
/// the single most important fidelity check in the whole port.
@Suite("UserProfileStore formulas")
struct UserProfileStoreTests {
    /// A fresh, isolated `UserDefaults` suite per test so tests never read or
    /// pollute the real App Group store or each other.
    private func makeStore() -> UserProfileStore {
        let suiteName = "test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        return UserProfileStore(defaults: defaults)
    }

    @Test("BMR matches the documented worked example: 28y male, 180cm, 85kg -> 1840 kcal")
    func bmrMaleWorkedExample() {
        let store = makeStore()
        store.age = 28
        store.heightCm = 180
        store.weightKg = 85
        store.isMale = true
        #expect(store.bmr() == 1840.0)
    }

    @Test("Daily calorie target matches the documented worked example: TDEE(1.3) - 500 = 1892 kcal")
    func dailyCalorieTargetWorkedExample() {
        let store = makeStore()
        store.age = 28
        store.heightCm = 180
        store.weightKg = 85
        store.isMale = true
        store.activityIndex = 2 // moderately active, 1.3x
        store.goalIndex = 0     // lose weight, -500 kcal
        #expect(store.dailyCalorieTarget() == 1892)
    }

    @Test("Female BMR subtracts 161 instead of adding 5")
    func bmrFemale() {
        let store = makeStore()
        store.age = 30
        store.heightCm = 165
        store.weightKg = 60
        store.isMale = false
        // 10*60 + 6.25*165 - 5*30 - 161 = 600 + 1031.25 - 150 - 161 = 1320.25
        #expect(abs(store.bmr() - 1320.25) < 0.001)
    }

    @Test("Without a profile, BMR falls back to 1700 and target to the 2000 default")
    func noProfileFallback() {
        let store = makeStore()
        #expect(store.hasProfile() == false)
        #expect(store.bmr() == 1700.0)
        #expect(store.dailyCalorieTarget() == UserProfileStore.defaultCalories)
    }

    @Test("An AI-computed calorie target always overrides the Mifflin-St Jeor estimate")
    func aiTargetOverridesEstimate() {
        let store = makeStore()
        store.age = 28
        store.heightCm = 180
        store.weightKg = 85
        store.aiCalorieTarget = 2_100
        #expect(store.dailyCalorieTarget() == 2_100)
    }

    @Test("Activity multipliers: sedentary 1.1x through extra active 1.6x")
    func activityMultipliers() {
        let store = makeStore()
        store.age = 30
        store.heightCm = 170
        store.weightKg = 70
        store.goalIndex = 1 // maintain, no offset
        let bmr = store.bmr()

        let expected: [Int: Double] = [0: 1.1, 1: 1.2, 2: 1.3, 3: 1.45, 4: 1.6]
        for (activity, multiplier) in expected.sorted(by: { $0.key < $1.key }) {
            store.activityIndex = activity
            #expect(store.dailyCalorieTarget() == Int(bmr * multiplier))
        }
    }

    @Test("Goal offsets: -500 to lose, 0 to maintain, +250 to build muscle")
    func goalOffsets() {
        let store = makeStore()
        store.goalIndex = 0
        #expect(store.goalOffset() == -500)
        store.goalIndex = 1
        #expect(store.goalOffset() == 0)
        store.goalIndex = 2
        #expect(store.goalOffset() == 250)
    }

    @Test("Macro ratios: lose 40/30/30, maintain 30/40/30, build 30/45/25")
    func macroRatios() {
        let store = makeStore()
        store.age = 28
        store.heightCm = 180
        store.weightKg = 85
        store.activityIndex = 2
        store.goalIndex = 0 // lose weight -> 1892 kcal, matching the worked example above
        let cal = Double(store.dailyCalorieTarget())

        let lose = store.macroTargets()
        #expect(lose.proteinG == Int(cal * 0.40 / 4))
        #expect(lose.carbsG == Int(cal * 0.30 / 4))
        #expect(lose.fatG == Int(cal * 0.30 / 9))

        store.goalIndex = 2 // build muscle
        let cal2 = Double(store.dailyCalorieTarget())
        let build = store.macroTargets()
        #expect(build.proteinG == Int(cal2 * 0.30 / 4))
        #expect(build.carbsG == Int(cal2 * 0.45 / 4))
        #expect(build.fatG == Int(cal2 * 0.25 / 9))
    }

    @Test("AI-computed macros override the ratio-based estimate")
    func aiMacrosOverrideRatios() {
        let store = makeStore()
        store.aiProteinTarget = 150
        store.aiCarbsTarget = 200
        store.aiFatTarget = 60
        let macros = store.macroTargets()
        #expect(macros.proteinG == 150)
        #expect(macros.carbsG == 200)
        #expect(macros.fatG == 60)
    }
}
