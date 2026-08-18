import SwiftUI
import ScaScanKit

/// Mirrors `nav_graph.xml`'s `apiKeyFragment` → `mainFragment` gate: returning
/// users with a saved key + model skip straight to the tab host; everyone
/// else sees first-run setup.
struct RootView: View {
    @Environment(AppContainer.self) private var container
    @State private var isSetUp: Bool

    init() {
        let keyStore = GeminiKeyStore.shared
        _isSetUp = State(initialValue: keyStore.hasKey() && !keyStore.selectedModel.isEmpty)
    }

    var body: some View {
        if isSetUp {
            MainTabView()
        } else {
            ApiKeySetupView { isSetUp = true }
        }
    }
}
