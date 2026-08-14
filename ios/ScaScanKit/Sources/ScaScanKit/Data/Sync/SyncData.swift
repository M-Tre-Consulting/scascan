import Foundation

/// Mirrors Android's `data.model.ProfileExport`.
public struct ProfileExport: Codable, Sendable, Equatable {
    public var name = ""
    public var age = 0
    public var heightCm = 0
    public var weightKg: Double = 0
    public var isMale = true
    public var activityIndex = 2
    public var goalIndex = 1
    public var aiCalorieTarget = 0
    public var aiProteinTarget = 0
    public var aiCarbsTarget = 0
    public var aiFatTarget = 0

    public init(
        name: String = "", age: Int = 0, heightCm: Int = 0, weightKg: Double = 0, isMale: Bool = true,
        activityIndex: Int = 2, goalIndex: Int = 1, aiCalorieTarget: Int = 0,
        aiProteinTarget: Int = 0, aiCarbsTarget: Int = 0, aiFatTarget: Int = 0
    ) {
        self.name = name
        self.age = age
        self.heightCm = heightCm
        self.weightKg = weightKg
        self.isMale = isMale
        self.activityIndex = activityIndex
        self.goalIndex = goalIndex
        self.aiCalorieTarget = aiCalorieTarget
        self.aiProteinTarget = aiProteinTarget
        self.aiCarbsTarget = aiCarbsTarget
        self.aiFatTarget = aiFatTarget
    }

    public init(from profile: UserProfileStore) {
        self.init(
            name: profile.name, age: profile.age, heightCm: profile.heightCm, weightKg: profile.weightKg,
            isMale: profile.isMale, activityIndex: profile.activityIndex, goalIndex: profile.goalIndex,
            aiCalorieTarget: profile.aiCalorieTarget, aiProteinTarget: profile.aiProteinTarget,
            aiCarbsTarget: profile.aiCarbsTarget, aiFatTarget: profile.aiFatTarget
        )
    }
}

/// A plain, `Codable` stand-in for `LogEntry` (a SwiftData `@Model` reference
/// type, which can't round-trip through `Codable` the same way). Mirrors
/// Android's approach of serializing the Room `LogEntry` entity directly —
/// same fields, just split into its own DTO here for a cleaner SwiftData
/// boundary.
public struct LogEntryExport: Codable, Sendable, Equatable {
    public var timestamp: Date
    public var foodName: String
    public var servingSize: String
    public var calories: Double
    public var protein: Double
    public var carbohydrates: Double
    public var fat: Double
    public var fiber: Double
    public var sugar: Double
    public var sodium: Double

    public init(
        timestamp: Date, foodName: String, servingSize: String, calories: Double,
        protein: Double, carbohydrates: Double, fat: Double, fiber: Double, sugar: Double, sodium: Double
    ) {
        self.timestamp = timestamp
        self.foodName = foodName
        self.servingSize = servingSize
        self.calories = calories
        self.protein = protein
        self.carbohydrates = carbohydrates
        self.fat = fat
        self.fiber = fiber
        self.sugar = sugar
        self.sodium = sodium
    }

    public init(from entry: LogEntry) {
        self.init(
            timestamp: entry.timestamp, foodName: entry.foodName, servingSize: entry.servingSize,
            calories: entry.calories, protein: entry.protein, carbohydrates: entry.carbohydrates,
            fat: entry.fat, fiber: entry.fiber, sugar: entry.sugar, sodium: entry.sodium
        )
    }

    public var asLogEntry: LogEntry {
        LogEntry(
            timestamp: timestamp, foodName: foodName, servingSize: servingSize, calories: calories,
            protein: protein, carbohydrates: carbohydrates, fat: fat, fiber: fiber, sugar: sugar, sodium: sodium
        )
    }
}

/// Mirrors Android's `data.model.SyncData` — the single JSON payload written
/// to (and read from) cloud storage: Google Drive's `appDataFolder` there,
/// this app's private CloudKit database here. Both are the platform's
/// per-app, user-private storage — a faithful match, not a downgrade.
public struct SyncData: Codable, Sendable, Equatable {
    public var profile = ProfileExport()
    public var logs: [LogEntryExport] = []
    public var lastUpdated = Date()

    public init(profile: ProfileExport = ProfileExport(), logs: [LogEntryExport] = [], lastUpdated: Date = .now) {
        self.profile = profile
        self.logs = logs
        self.lastUpdated = lastUpdated
    }
}
