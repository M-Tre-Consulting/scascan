/// Mirrors Android's `domain.usecase.ScanBarcodeUseCase`.
public struct ScanBarcodeUseCase: Sendable {
    private let repository: NutritionRepository

    public init(repository: NutritionRepository) {
        self.repository = repository
    }

    public func callAsFunction(_ barcode: String) async throws -> NutritionFacts {
        try await repository.analyzeBarcode(barcode)
    }
}
