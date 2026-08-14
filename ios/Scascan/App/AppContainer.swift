import Foundation
import SwiftData
import ScaScanKit

/// Composition root. Replaces Android's Hilt `AppModule` — plain manual
/// dependency injection via the SwiftUI environment is the idiomatic Apple
/// pattern, so no DI framework is used.
@MainActor
@Observable
final class AppContainer {
    let modelContainer: ModelContainer

    let keyStore: GeminiKeyStore
    let profileStore: UserProfileStore
    let nutritionRepository: NutritionRepository
    let logRepository: LogRepository
    let analysisManager: AnalysisManager

    /// The active "fix this entry" target, if any. Set from Log, Search, or
    /// the analysis result sheet; presented once at the `MainTabView` level.
    var fixTarget: FixEntryTarget?

    init(modelContainer: ModelContainer = ScaScanSchema.makeContainer()) {
        self.modelContainer = modelContainer
        self.keyStore = .shared
        self.profileStore = .shared

        let nutritionRepository = NutritionRepository()
        self.nutritionRepository = nutritionRepository

        // TODO(Phase 5): hook this up to a WidgetKit timeline reload, mirroring
        // Android's SummaryWidgetProvider.triggerUpdate(context) broadcast.
        self.logRepository = LogRepository(modelContainer: modelContainer, onDataChanged: {})

        self.analysisManager = AnalysisManager(repository: nutritionRepository)
    }
}
