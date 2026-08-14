import AppIntents
import ScaScanKit

/// Powers the widget's one-tap "+250ml" button — an interactive App Intent
/// (iOS 17+), the direct native equivalent of Android's widget `PendingIntent`
/// broadcast to `SummaryWidgetProvider.ACTION_ADD_WATER`. Runs in the widget
/// extension's own process against the shared App Group store, so it works
/// without launching the app.
struct AddWaterIntent: AppIntent {
    static var title: LocalizedStringResource { "Add Water" }
    static var description: IntentDescription { IntentDescription("Logs 250ml of water to today's total.") }

    @MainActor
    func perform() async throws -> some IntentResult {
        let container = ScaScanSchema.makeContainer()
        let repository = LogRepository(modelContainer: container)
        try? repository.addWater(250)
        return .result()
    }
}

/// The widget's "undo last water entry" tap target — mirrors Android's
/// `SummaryWidgetProvider.ACTION_UNDO_WATER` broadcast receiver.
struct UndoWaterIntent: AppIntent {
    static var title: LocalizedStringResource { "Undo Water" }
    static var description: IntentDescription { IntentDescription("Removes the most recently logged water entry.") }

    @MainActor
    func perform() async throws -> some IntentResult {
        let container = ScaScanSchema.makeContainer()
        let repository = LogRepository(modelContainer: container)
        try? repository.removeLastWater()
        return .result()
    }
}
