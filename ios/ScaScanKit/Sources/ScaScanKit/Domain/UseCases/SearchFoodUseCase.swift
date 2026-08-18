/// Mirrors Android's `domain.usecase.SearchFoodUseCase`.
public struct SearchFoodUseCase: Sendable {
    private let repository: NutritionRepository

    public init(repository: NutritionRepository) {
        self.repository = repository
    }

    public func callAsFunction(_ query: String) async throws -> NutritionFacts {
        try await repository.searchFood(query)
    }
}
