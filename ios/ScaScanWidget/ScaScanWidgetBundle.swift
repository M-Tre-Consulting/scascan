import WidgetKit
import SwiftUI

/// Mirrors Android's `ui.widget.SummaryWidgetProvider` — a home screen
/// glanceable summary of today's calories, macros, and water.
@main
struct ScaScanWidgetBundle: WidgetBundle {
    var body: some Widget {
        SummaryWidget()
    }
}
