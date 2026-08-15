import AppIntents
import ScaScanKit

/// Powers the widget's one-tap "+250ml" button — an interactive App Intent
/// (iOS 17+), the direct native equivalent of Android's widget `PendingIntent`
/// broadcast to `SummaryWidgetProvider.ACTION_ADD_WATER`. Runs in the widget
/// extension's own process against the shared App Group store, so it works
/// without launching the app.
struct AddWaterIntent: AppIntent {
    static var title: LocalizedStringResource { "Add Water" }
    static var description: IntentDescription { IntentDescription("Logs water to today's total. Amount is set in ScaScan's Settings.") }

    @MainActor
    func perform() async throws -> some IntentResult {
        let container = ScaScanSchema.makeContainer()
        let repository = LogRepository(modelContainer: container)
        // User-configurable (Settings ▸ Water amounts), defaults to 250ml —
        // was hardcoded before, matching Android's old fixed +250ml action.
        try? repository.addWater(UserProfileStore.shared.widgetWaterAmountMl)
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
