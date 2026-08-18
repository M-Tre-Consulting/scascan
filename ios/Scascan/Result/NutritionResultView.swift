import SwiftUI
import ScaScanKit

/// Mirrors Android's `NutritionResultFragment` — the full macro/micro
/// breakdown reached via "View details" from the analysis sheet.
struct NutritionResultView: View {
    let facts: NutritionFacts

    @Environment(AppContainer.self) private var container
    @State private var added = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(facts.foodName)
                        .font(.largeTitle.bold())
                    Text(facts.servingSize)
                        .foregroundStyle(.secondary)
                }

                SectionCard {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Per serving")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        HStack(alignment: .lastTextBaseline, spacing: 4) {
                            Text("\(Int(facts.calories.rounded()))")
                                .font(.system(size: 48, weight: .bold, design: .rounded))
                            Text("kcal")
                                .font(.title3)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                SectionCard(title: "Nutritional Facts") {
                    VStack(spacing: 12) {
                        nutrientRow("Protein", value: facts.protein, unit: "g")
                        nutrientRow("Carbohydrates", value: facts.carbohydrates, unit: "g")
                        nutrientRow("Fat", value: facts.fat, unit: "g")
                        Divider()
                        nutrientRow("Fiber", value: facts.fiber, unit: "g")
                        nutrientRow("Sugar", value: facts.sugar, unit: "g")
                        nutrientRow("Sodium", value: facts.sodium, unit: "mg", decimals: 0)
                    }
                }

                Text("Values are AI-estimated and may vary. Always verify with official food labels.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                Button(added ? "Added to today's log" : "Add to log") {
                    _ = try? container.logRepository.addEntry(facts)
                    added = true
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
                .disabled(added)
            }
            .padding(20)
        }
        .navigationTitle("Nutritional Facts")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func nutrientRow(_ label: String, value: Double, unit: String, decimals: Int = 1) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(String(format: "%.\(decimals)f %@", value, unit))
                .foregroundStyle(.secondary)
        }
        .font(.subheadline)
    }
}
