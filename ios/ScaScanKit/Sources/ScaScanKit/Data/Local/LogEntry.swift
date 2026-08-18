import Foundation
import SwiftData

/// Mirrors Android's `data.local.LogEntry` Room entity (table `log_entries`).
/// SwiftData synthesizes the primary key, replacing Room's `autoGenerate id`.
@Model
public final class LogEntry {
    /// A stable identity independent of SwiftData's own `persistentModelID`
    /// (which isn't meant to be persisted outside the store). Used to tag the
    /// matching HealthKit nutrition correlation for this entry, so it can be
    /// found again and deleted/replaced when the entry is edited or removed —
    /// see `HealthKitManager.writeDietaryEntry`.
    ///
    /// The `= UUID()` default has to live right here, not just on the `init`
    /// parameter below: SwiftData's lightweight migration reads the default
    /// from the property declaration itself to backfill this column on rows
    /// that already exist on disk from before this field existed. A default
    /// that only exists on the initializer is invisible to the migrator —
    /// it'd see a new non-optional column with no way to fill existing rows
    /// and fail to open the store (crashing at launch for any upgrading
    /// install, before this default was added here).
    public var id: UUID = UUID()
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
        id: UUID = UUID(),
        timestamp: Date = .now,
        foodName: String,
        servingSize: String,
        calories: Double,
        protein: Double,
        carbohydrates: Double,
        fat: Double,
        fiber: Double,
        sugar: Double,
        sodium: Double
    ) {
        self.id = id
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

    public convenience init(from facts: NutritionFacts, timestamp: Date = .now) {
        self.init(
            timestamp: timestamp,
            foodName: facts.foodName,
            servingSize: facts.servingSize,
            calories: facts.calories,
            protein: facts.protein,
            carbohydrates: facts.carbohydrates,
            fat: facts.fat,
            fiber: facts.fiber,
            sugar: facts.sugar,
            sodium: facts.sodium
        )
    }

    public var asNutritionFacts: NutritionFacts {
        NutritionFacts(
            foodName: foodName,
            servingSize: servingSize,
            calories: calories,
            protein: protein,
            carbohydrates: carbohydrates,
            fat: fat,
            fiber: fiber,
            sugar: sugar,
            sodium: sodium
        )
    }
}
