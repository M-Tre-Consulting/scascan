import SwiftUI
import ScaScanKit

/// Mirrors Android's `DailySummaryBottomSheetFragment`.
struct DailySummarySheet: View {
    let dateLabel: String
    let entries: [LogEntry]
    let water: [WaterLog]
    let target: LogViewState.TargetInfo
    let adaptive: LogViewState.AdaptiveState
    let liveTarget: Int

    private var totalCalories: Int { Int(entries.reduce(0) { $0 + $1.calories }.rounded()) }
    private var totalProtein: Int { Int(entries.reduce(0) { $0 + $1.protein }.rounded()) }
    private var totalCarbs: Int { Int(entries.reduce(0) { $0 + $1.carbohydrates }.rounded()) }
    private var totalFat: Int { Int(entries.reduce(0) { $0 + $1.fat }.rounded()) }
    private var totalFiber: Double { entries.reduce(0) { $0 + $1.fiber } }
    private var totalSodium: Double { entries.reduce(0) { $0 + $1.sodium } }
    private var totalWater: Int { water.reduce(0) { $0 + $1.amountMl } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(dateLabel)
                    .font(.title2.bold())

                SectionCard(title: "Calories") {
                    caloriesSection
                }

                if target.macros.proteinG > 0 {
                    SectionCard(title: "Macros") {
                        VStack(spacing: 12) {
                            LabeledProgress(label: "Protein", caption: "\(totalProtein)g / \(target.macros.proteinG)g", value: Double(totalProtein), total: Double(target.macros.proteinG))
                            LabeledProgress(label: "Carbs", caption: "\(totalCarbs)g / \(target.macros.carbsG)g", value: Double(totalCarbs), total: Double(target.macros.carbsG))
                            LabeledProgress(label: "Fat", caption: "\(totalFat)g / \(target.macros.fatG)g", value: Double(totalFat), total: Double(target.macros.fatG))
                        }
                    }
                }

                SectionCard(title: "Other nutrients") {
                    VStack(spacing: 8) {
                        HStack { Text("Fiber"); Spacer(); Text(String(format: "%.1f g", totalFiber)).foregroundStyle(.secondary) }
                        HStack { Text("Sodium"); Spacer(); Text(String(format: "%.0f mg", totalSodium)).foregroundStyle(.secondary) }
                        HStack { Text("Water"); Spacer(); Text("\(totalWater) ml").foregroundStyle(.secondary) }
                    }
                    .font(.subheadline)
                }

                SectionCard(title: "Goal") {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(Self.goalLabels[safe: target.goalIndex] ?? Self.goalLabels[1])
                            .font(.subheadline.weight(.medium))
                        Text(target.isAiComputed ? "Targets computed by AI" : "Targets based on profile estimate")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(20)
        }
    }

    @ViewBuilder
    private var caloriesSection: some View {
        let remaining = liveTarget - totalCalories
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text("\(totalCalories)")
                    .font(.system(size: 40, weight: .bold, design: .rounded))
                Text("/ \(liveTarget) kcal")
                    .foregroundStyle(.secondary)
            }
            ProgressView(value: liveTarget > 0 ? min(Double(totalCalories) / Double(liveTarget), 1) : 0)
            Text(remaining >= 0 ? "\(remaining) kcal remaining" : "\(-remaining) kcal over goal")
                .font(.subheadline)
                .foregroundStyle(remaining >= 0 ? Color.secondary : Color.red)

            if target.bleedthroughKcal != 0 {
                Text("\(target.bleedthroughKcal > 0 ? "+" : "")\(target.bleedthroughKcal) kcal from yesterday")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if case .active(let a) = adaptive, a.activeKcal > 0 || a.steps > 0 {
                HStack(spacing: 16) {
                    Label("\(a.steps) steps", systemImage: "figure.walk")
                    Label("\(Int(a.activeKcal.rounded())) kcal", systemImage: "flame")
                    Label(String(format: "%.1f km", Double(a.steps) * 0.0008), systemImage: "location")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    private static let goalLabels = ["Lose weight", "Maintain weight", "Build muscle"]
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
