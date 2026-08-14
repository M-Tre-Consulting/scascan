import Foundation
import SwiftData
import ScaScanKit

/// What's being corrected via the "Fix" flow: either a not-yet-logged result
/// (from the analysis sheet) or an already-logged entry (from the Log tab).
/// Lives on `AppContainer` so any screen can trigger the fix sheet that's
/// presented once, at the `MainTabView` level.
enum FixEntryTarget: Identifiable {
    case pending(NutritionFacts)
    case logged(LogEntry)

    var id: String {
        switch self {
        case .pending(let facts): return "pending-\(facts.foodName)"
        case .logged(let entry): return "logged-\(entry.persistentModelID)"
        }
    }

    var currentFoodName: String {
        switch self {
        case .pending(let facts): return facts.foodName
        case .logged(let entry): return entry.foodName
        }
    }
}
