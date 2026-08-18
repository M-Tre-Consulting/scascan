import SwiftUI
import ScaScanKit

/// Mirrors Android's `SearchFragment` — free-text food/meal lookup. Fully
/// wired end-to-end (unlike Camera/Barcode, it needs no device sensors).
struct SearchView: View {
    @Environment(AppContainer.self) private var container
    @State private var query = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Describe a food or meal to get its nutritional facts.")
                .foregroundStyle(.secondary)

            TextField("e.g. Chicken breast, 100g", text: $query)
                .textFieldStyle(.roundedBorder)
                .focused($focused)
                .submitLabel(.search)
                .onSubmit(analyze)

            Button("Analyze", action: analyze)
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
                .disabled(query.trimmingCharacters(in: .whitespaces).isEmpty)

            Spacer()
        }
        .padding(20)
        .navigationTitle("Search Food")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { focused = true }
    }

    private func analyze() {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        focused = false
        container.analysisManager.analyzeSearch(trimmed)
    }
}
