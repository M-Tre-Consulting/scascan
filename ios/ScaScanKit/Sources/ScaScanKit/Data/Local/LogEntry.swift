import Foundation
import SwiftData

/// Mirrors Android's `data.local.LogEntry` Room entity (table `log_entries`).
/// SwiftData synthesizes the primary key, replacing Room's `autoGenerate id`.
@Model
public final class LogEntry {
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
