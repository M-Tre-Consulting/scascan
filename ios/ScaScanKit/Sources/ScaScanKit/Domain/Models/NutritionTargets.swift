import Foundation

/// Mirrors Android's `data.model.NutritionTargets` — the shape Gemini is asked
/// to return when computing AI-personalized daily targets.
public struct NutritionTargets: Codable, Hashable, Sendable {
    public var dailyCalories: Int
    public var proteinGrams: Int
    public var carbsGrams: Int
    public var fatGrams: Int

    public init(dailyCalories: Int, proteinGrams: Int, carbsGrams: Int, fatGrams: Int) {
        self.dailyCalories = dailyCalories
        self.proteinGrams = proteinGrams
        self.carbsGrams = carbsGrams
        self.fatGrams = fatGrams
    }
}
