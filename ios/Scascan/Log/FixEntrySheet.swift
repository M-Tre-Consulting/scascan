import SwiftUI

/// Mirrors Android's `FixEntryBottomSheetFragment` — used both for a
/// not-yet-logged analysis result and for an already-logged entry.
struct FixEntrySheet: View {
    let target: FixEntryTarget
    let onApply: (String) -> Void

    @State private var correction = ""
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Fix this entry")
                .font(.title2.bold())
            Text("Describe the correct food and ScaScan will recalculate the nutritional values.")
                .foregroundStyle(.secondary)

            VStack(alignment: .leading, spacing: 4) {
                Text("Currently identified as:")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(target.currentFoodName)
                    .font(.subheadline.weight(.medium))
            }

            VStack(alignment: .leading, spacing: 4) {
                TextField("Correct food (e.g. Turkey breast, 150g)", text: $correction)
                    .textFieldStyle(.roundedBorder)
                if let error {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }

            HStack {
                Button("Cancel", role: .cancel) { dismiss() }
                    .buttonStyle(.bordered)
                Spacer()
                Button("Apply fix", action: apply)
                    .buttonStyle(.borderedProminent)
            }

            Spacer()
        }
        .padding(20)
    }

    private func apply() {
        let trimmed = correction.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            error = "Please describe the correct food"
            return
        }
        error = nil
        onApply(trimmed)
    }
}
