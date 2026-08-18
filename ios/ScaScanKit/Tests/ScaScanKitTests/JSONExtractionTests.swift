import Testing
import Foundation
@testable import ScaScanKit

@Suite("JSONExtraction — parsing Gemini's text responses")
struct JSONExtractionTests {
    @Test("Decodes a clean JSON object")
    func cleanJSON() throws {
        let text = #"{"foodName":"Chicken breast","servingSize":"100g","calories":165,"protein":31,"carbohydrates":0,"fat":3.6,"fiber":0,"sugar":0,"sodium":74}"#
        let facts = try JSONExtraction.decodeFirstObject(NutritionFacts.self, from: text)
        #expect(facts.foodName == "Chicken breast")
        #expect(facts.calories == 165)
        #expect(facts.protein == 31)
    }

    @Test("Strips markdown fences and leading/trailing prose Gemini sometimes adds anyway")
    func markdownWrapped() throws {
        let text = """
        Sure, here's the nutritional breakdown:
        ```json
        {"foodName":"Apple","servingSize":"1 medium (182g)","calories":95,"protein":0.5,"carbohydrates":25,"fat":0.3,"fiber":4.4,"sugar":19,"sodium":2}
        ```
        Let me know if you need anything else!
        """
        let facts = try JSONExtraction.decodeFirstObject(NutritionFacts.self, from: text)
        #expect(facts.foodName == "Apple")
        #expect(facts.fiber == 4.4)
    }

    @Test("Throws noJSONInResponse when there's no JSON object at all")
    func noJSON() {
        #expect(throws: NutritionRepositoryError.noJSONInResponse) {
            try JSONExtraction.decodeFirstObject(NutritionFacts.self, from: "Sorry, I can't identify this food.")
        }
    }

    @Test("Throws malformedJSON on a syntactically broken object, not the raw decoding error")
    func malformedJSON() {
        let text = #"{"foodName":"Banana", "calories": ,}"#
        #expect(throws: NutritionRepositoryError.malformedJSON) {
            try JSONExtraction.decodeFirstObject(NutritionFacts.self, from: text)
        }
    }

    @Test("Decodes NutritionTargets the same way, for computeTargets")
    func nutritionTargets() throws {
        let text = #"{"dailyCalories":2200,"proteinGrams":140,"carbsGrams":250,"fatGrams":70}"#
        let targets = try JSONExtraction.decodeFirstObject(NutritionTargets.self, from: text)
        #expect(targets.dailyCalories == 2_200)
        #expect(targets.proteinGrams == 140)
    }
}
