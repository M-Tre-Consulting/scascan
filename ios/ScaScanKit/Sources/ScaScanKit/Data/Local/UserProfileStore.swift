import Foundation

/// Mirrors Android's `data.local.UserProfileStore` exactly — same UserDefaults
/// keys, same default fallbacks, same Mifflin-St Jeor / TDEE / macro-ratio
/// constants. This file is the source of truth for the app's nutrition math;
/// keep it byte-for-byte in sync with the Kotlin original if that ever changes.
public final class UserProfileStore: @unchecked Sendable {
    public static let shared = UserProfileStore()
    public static let defaultCalories = 2_000

    private let d: UserDefaults

    public init(defaults: UserDefaults = AppGroup.sharedDefaults) {
        self.d = defaults
    }

    private enum Keys {
        static let name = "user_name"
        static let age = "age"
        static let sexMale = "sex_male"
        static let heightCm = "height_cm"
        static let weightKg = "weight_kg"
        static let activityIndex = "activity_idx"
        static let goalIndex = "goal_idx"
        static let aiCalories = "ai_calories"
        static let aiProtein = "ai_protein"
        static let aiCarbs = "ai_carbs"
        static let aiFat = "ai_fat"
        static let syncEmail = "sync_email"
        static let waterReminders = "water_reminders"
        static let lastActiveCalories = "last_active_kcal"
    }

    public var name: String {
        get { d.string(forKey: Keys.name) ?? "" }
        set { d.set(newValue, forKey: Keys.name) }
    }

    public var age: Int {
        get { d.integer(forKey: Keys.age) }
        set { d.set(newValue, forKey: Keys.age) }
    }

    public var isMale: Bool {
        get { d.object(forKey: Keys.sexMale) == nil ? true : d.bool(forKey: Keys.sexMale) }
        set { d.set(newValue, forKey: Keys.sexMale) }
    }

    public var heightCm: Int {
        get { d.integer(forKey: Keys.heightCm) }
        set { d.set(newValue, forKey: Keys.heightCm) }
    }

    public var weightKg: Double {
        get { d.double(forKey: Keys.weightKg) }
        set { d.set(newValue, forKey: Keys.weightKg) }
    }

    /// 0 = sedentary → 4 = extra active. Defaults to 2 (moderately active) when unset.
    public var activityIndex: Int {
        get { d.object(forKey: Keys.activityIndex) == nil ? 2 : d.integer(forKey: Keys.activityIndex) }
        set { d.set(newValue, forKey: Keys.activityIndex) }
    }

    /// 0 = lose weight, 1 = maintain, 2 = build muscle. Defaults to 1 (maintain) when unset.
    public var goalIndex: Int {
        get { d.object(forKey: Keys.goalIndex) == nil ? 1 : d.integer(forKey: Keys.goalIndex) }
        set { d.set(newValue, forKey: Keys.goalIndex) }
    }

    /// AI-computed daily calorie target. 0 = not computed yet.
    public var aiCalorieTarget: Int {
        get { d.integer(forKey: Keys.aiCalories) }
        set { d.set(newValue, forKey: Keys.aiCalories) }
    }

    public var aiProteinTarget: Int {
        get { d.integer(forKey: Keys.aiProtein) }
        set { d.set(newValue, forKey: Keys.aiProtein) }
    }

    public var aiCarbsTarget: Int {
        get { d.integer(forKey: Keys.aiCarbs) }
        set { d.set(newValue, forKey: Keys.aiCarbs) }
    }

    public var aiFatTarget: Int {
        get { d.integer(forKey: Keys.aiFat) }
        set { d.set(newValue, forKey: Keys.aiFat) }
    }

    public var syncEmail: String {
        get { d.string(forKey: Keys.syncEmail) ?? "" }
        set { d.set(newValue, forKey: Keys.syncEmail) }
    }

    public var waterRemindersEnabled: Bool {
        get { d.bool(forKey: Keys.waterReminders) }
        set { d.set(newValue, forKey: Keys.waterReminders) }
    }

    /// Cached active calories from HealthKit, used as a fallback when a background
    /// refresh (e.g. the widget timeline) can't get a live reading.
    public var lastActiveCalories: Double {
        get { d.double(forKey: Keys.lastActiveCalories) }
        set { d.set(newValue, forKey: Keys.lastActiveCalories) }
    }

    public func hasProfile() -> Bool { age > 0 && heightCm > 0 && weightKg > 0 }

    /// Mifflin-St Jeor Equation (standard industry formula).
    public func bmr() -> Double {
        guard hasProfile() else { return 1_700.0 }
        return isMale
            ? (10.0 * weightKg) + (6.25 * Double(heightCm)) - (5.0 * Double(age)) + 5.0
            : (10.0 * weightKg) + (6.25 * Double(heightCm)) - (5.0 * Double(age)) - 161.0
    }

    /// Returns the AI-computed target if available, otherwise falls back to
    /// Mifflin-St Jeor TDEE with a goal adjustment.
    public func dailyCalorieTarget() -> Int {
        if aiCalorieTarget > 0 { return aiCalorieTarget }
        guard hasProfile() else { return Self.defaultCalories }

        // Refined base multipliers for TDEE. Slightly lower than textbook values
        // because HealthKit active-energy is added on top separately.
        let baseMultiplier: Double
        switch min(max(activityIndex, 0), 4) {
        case 0: baseMultiplier = 1.1   // Sedentary
        case 1: baseMultiplier = 1.2   // Lightly active
        case 2: baseMultiplier = 1.3   // Moderately active
        case 3: baseMultiplier = 1.45  // Very active
        case 4: baseMultiplier = 1.6   // Extra active
        default: baseMultiplier = 1.2
        }

        let tdee = bmr() * baseMultiplier
        return Int(tdee + Double(goalOffset()))
    }

    /// Returns macro targets in grams. Uses AI-computed values when available;
    /// falls back to goal-based ratios derived from the calorie target.
    ///
    /// Ratios (protein/carbs/fat):
    ///   Lose weight  → 40% / 30% / 30% (higher protein to preserve muscle)
    ///   Maintain     → 30% / 40% / 30% (balanced)
    ///   Build muscle → 30% / 45% / 25% (high carbs + protein for hypertrophy)
    public func macroTargets() -> MacroTargets {
        if aiProteinTarget > 0 {
            return MacroTargets(proteinG: aiProteinTarget, carbsG: aiCarbsTarget, fatG: aiFatTarget)
        }
        let cal = Double(dailyCalorieTarget())
        switch min(max(goalIndex, 0), 2) {
        case 0:
            return MacroTargets(proteinG: Int(cal * 0.40 / 4), carbsG: Int(cal * 0.30 / 4), fatG: Int(cal * 0.30 / 9))
        case 2:
            return MacroTargets(proteinG: Int(cal * 0.30 / 4), carbsG: Int(cal * 0.45 / 4), fatG: Int(cal * 0.25 / 9))
        default:
            return MacroTargets(proteinG: Int(cal * 0.30 / 4), carbsG: Int(cal * 0.40 / 4), fatG: Int(cal * 0.30 / 9))
        }
    }

    public func goalOffset() -> Int {
        switch min(max(goalIndex, 0), 2) {
        case 0: return -500 // Lose weight: ~0.5kg/week deficit
        case 2: return 250  // Build muscle: surplus
        default: return 0   // Maintain
        }
    }
}
