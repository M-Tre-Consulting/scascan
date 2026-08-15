import SwiftUI

/// Mirrors Android's `FixEntryBottomSheetFragment` — used both for a
/// not-yet-logged analysis result and for an already-logged entry.
///
/// `onApply` is `async` and reports back success/failure instead of being a
/// fire-and-forget callback: previously the sheet closed the instant "Apply
/// fix" was tapped, before the Gemini request even returned, so a network
/// error or bad response silently vanished with the entry left unchanged —
/// indistinguishable from "nothing happened". Now the sheet stays open with
/// a spinner while the request is in flight, and shows the error inline
/// (with the typed correction preserved) so a failure can actually be seen
/// and retried, and only dismisses once the fix has really been applied.
enum FixOutcome {
    case success
    case failure(String)
}

struct FixEntrySheet: View {
    let target: FixEntryTarget
    let onApply: (String) async -> FixOutcome

    @State private var correction = ""
    @State private var error: String?
    @State private var isApplying = false
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
                    .disabled(isApplying)
                if let error {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }

            HStack {
                Button("Cancel", role: .cancel) { dismiss() }
                    .buttonStyle(.bordered)
                    .disabled(isApplying)
                Spacer()
                Button(action: apply) {
                    if isApplying {
                        ProgressView()
                            .controlSize(.small)
                            .tint(.white)
                    } else {
                        Text("Apply fix")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(isApplying)
            }

            Spacer()
        }
        .padding(20)
        .interactiveDismissDisabled(isApplying)
    }

    private func apply() {
        let trimmed = correction.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            error = "Please describe the correct food"
            return
        }
        error = nil
        isApplying = true
        Task {
            let outcome = await onApply(trimmed)
            isApplying = false
            switch outcome {
            case .success:
                dismiss()
            case .failure(let message):
                error = message
            }
        }
    }
}
