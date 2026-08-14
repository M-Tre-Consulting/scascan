import UIKit

/// Mirrors Android's `domain.usecase.AnalyzeFoodImageUseCase`.
public struct AnalyzeFoodImageUseCase: Sendable {
    private let repository: NutritionRepository

    public init(repository: NutritionRepository) {
        self.repository = repository
    }

    public func callAsFunction(_ image: UIImage) async throws -> NutritionFacts {
        try await repository.analyzeImage(image)
    }
}
