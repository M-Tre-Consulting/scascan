import Foundation
import SwiftData
import WidgetKit
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
    let healthManager: HealthKitManager
    let nutritionRepository: NutritionRepository
    let logRepository: LogRepository
    let analysisManager: AnalysisManager

    /// The active "fix this entry" target, if any. Set from Log, Search, or
    /// the analysis result sheet; presented once at the `MainTabView` level.
    var fixTarget: FixEntryTarget?

    /// An entry that was just auto-added (currently only by voice logging,
    /// which skips the usual confirm-before-add sheet) and can still be
    /// undone. `MainTabView` renders this as a briefly-shown banner.
    struct PendingUndo: Identifiable {
        let id = UUID()
        let message: String
        let entry: LogEntry
    }
    var pendingUndo: PendingUndo?

    /// Set from `scascan://` URL handling (widget taps, quick actions) —
    /// `MainTabView` observes this to switch tabs / push a route. Mirrors
    /// Android's `MainActivity.handleIntent` bundling `start_tab`.
    var deepLinkTab: Int?
    var deepLinkRoute: Route?

    init(modelContainer: ModelContainer = ScaScanSchema.makeContainer()) {
        self.modelContainer = modelContainer
        self.keyStore = .shared
        self.profileStore = .shared
        self.healthManager = .shared

        let nutritionRepository = NutritionRepository()
        self.nutritionRepository = nutritionRepository

        // Mirrors Android's SummaryWidgetProvider.triggerUpdate(context) broadcast.
        self.logRepository = LogRepository(
            modelContainer: modelContainer,
            health: HealthKitManager.shared,
            onDataChanged: { WidgetCenter.shared.reloadAllTimelines() }
        )

        self.analysisManager = AnalysisManager(repository: nutritionRepository)

        // See `VoiceLogSignal`'s doc comment: fires the moment Siri (or the
        // Shortcuts app) runs `StartVoiceLogIntent`, from any app state —
        // including while this app is already active in the foreground, when
        // a scenePhase transition alone wouldn't happen and ScascanApp's own
        // checks would never get a reason to re-run.
        VoiceLogSignal.observe { [weak self] in
            Task { @MainActor in
                self?.consumePendingVoiceLogRequestIfNeeded()
            }
        }
    }

    /// Checks the flag `StartVoiceLogIntent` (Siri / Shortcuts) leaves behind.
    /// If set, opens straight into voice logging. Called from `ScascanApp` on
    /// launch/foreground, and from the `VoiceLogSignal` observer above for the
    /// case where the app was already foregrounded when the intent ran.
    @discardableResult
    func consumePendingVoiceLogRequestIfNeeded() -> Bool {
        guard profileStore.pendingVoiceLogRequest else { return false }
        profileStore.pendingVoiceLogRequest = false
        deepLinkTab = 0
        deepLinkRoute = .voice
        return true
    }
}
