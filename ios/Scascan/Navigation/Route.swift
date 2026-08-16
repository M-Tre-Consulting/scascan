import Foundation
import ScaScanKit

/// Mirrors the destinations in Android's `nav_graph.xml`.
enum Route: Hashable {
    case camera
    case barcode
    case search
    case voice
    case result(NutritionFacts)
    case settings
}
