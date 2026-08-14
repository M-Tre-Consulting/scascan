import Foundation
import UIKit
import Observation

/// Mirrors Android's `data.analysis.AnalysisManager` — a shared, observable
/// coordinator for "analyze this food" requests kicked off from Camera,
/// Barcode Scan, or Search, so the result sheet can be shown from a single
/// place (`MainTabView`) regardless of which screen started the request.
///
/// Android backs this with a WorkManager one-time work request so the job
/// survives process death. Swift's structured concurrency (`Task`) gives the
/// same "cancellable unit of background work with observable state" for a
/// request this short-lived, without needing OS-level job scheduling.
@MainActor
@Observable
public final class AnalysisManager {
    public enum State: Equatable {
        case idle
        case processing
        case complete(NutritionFacts)
        case error(String)
    }

    public private(set) var state: State = .idle

    private let repository: NutritionRepository
    private var currentTask: Task<Void, Never>?

    public init(repository: NutritionRepository) {
        self.repository = repository
    }

    public func analyze(_ image: UIImage) {
        run { try await self.repository.analyzeImage(image) }
    }

    /// Barcode captured as a photo — OCR's the barcode number via Gemini vision,
    /// then falls back to OpenFoodFacts / pure vision identification.
    public func analyzeBarcodeImage(_ image: UIImage) {
        run { try await self.repository.analyzeBarcodeImage(image) }
    }

    /// Barcode already decoded natively (VisionKit `DataScannerViewController`)
    /// — skips OCR entirely and goes straight to OpenFoodFacts lookup.
    public func analyzeBarcode(_ code: String) {
        run { try await self.repository.analyzeBarcode(code) }
    }

    public func analyzeSearch(_ query: String) {
        run { try await self.repository.searchFood(query) }
    }

    public func fixPending(_ originalFacts: NutritionFacts, correction: String) {
        guard state != .processing else { return }
        run {
            try await self.repository.fixEntry(
                foodName: originalFacts.foodName,
                servingSize: originalFacts.servingSize,
                correction: correction
            )
        }
    }

    public func dismiss() {
        currentTask?.cancel()
        state = .idle
    }

    private func run(_ operation: @escaping () async throws -> NutritionFacts) {
        guard state != .processing else { return }
        state = .processing
        currentTask?.cancel()
        currentTask = Task {
            do {
                let facts = try await operation()
                guard !Task.isCancelled else { return }
                state = .complete(facts)
            } catch {
                guard !Task.isCancelled else { return }
                state = .error(error.localizedDescription)
            }
        }
    }
}
