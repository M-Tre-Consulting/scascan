import Foundation

/// Mirrors Android's `data.model.NutritionFacts` field-for-field.
public struct NutritionFacts: Codable, Hashable, Sendable {
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
}
