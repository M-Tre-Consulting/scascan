import SwiftUI
import ScaScanKit

/// Mirrors Android's `AnalysisResultBottomSheetFragment` — shown whenever
/// `AnalysisManager` completes, regardless of which tab/screen started it.
struct AnalysisResultSheet: View {
    let facts: NutritionFacts
    let onAdd: () -> Void
    let onFix: () -> Void
    let onViewDetails: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 4) {
                Text(facts.foodName)
                    .font(.title2.bold())
                Text("\(Int(facts.calories.rounded())) kcal · \(facts.servingSize)")
                    .foregroundStyle(.secondary)
                Text("\(Int(facts.protein.rounded()))g protein · \(Int(facts.carbohydrates.rounded()))g carbs · \(Int(facts.fat.rounded()))g fat")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            VStack(spacing: 10) {
                Button("Add to log", action: onAdd)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .frame(maxWidth: .infinity)

                HStack(spacing: 10) {
                    Button("Fix", action: onFix)
                        .buttonStyle(.bordered)
                    Button("View details", action: onViewDetails)
                        .buttonStyle(.bordered)
                }
                .frame(maxWidth: .infinity)

                Button("Dismiss", role: .cancel, action: onDismiss)
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding(20)
    }
}
