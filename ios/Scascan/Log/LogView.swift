import SwiftUI
import SwiftData
import ScaScanKit

/// Mirrors Android's `LogFragment` — date navigation, calorie/macro summary,
/// water tracking, the adaptive-target card, and the meal entry list.
struct LogView: View {
    @Environment(AppContainer.self) private var container
    @State private var state: LogViewState?
    @State private var showingSummary = false

    var body: some View {
        Group {
            if let state {
                content(state)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Log")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if state == nil {
                state = LogViewState(repository: container.logRepository, health: container.healthManager)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { state?.reload() }
        }
    }

    @Environment(\.scenePhase) private var scenePhase

    @ViewBuilder
    private func content(_ state: LogViewState) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                dateNavigation(state)
                calorieSummary(state)
                macroSummary(state)
                waterSection(state)
                adaptiveCard(state)
                workoutsSection(state)
                entriesSection(state)
            }
            .padding(20)
        }
        .sheet(isPresented: $showingSummary) {
            DailySummarySheet(
                dateLabel: state.selectedDateLabel,
                entries: state.entries,
                water: state.water,
                target: state.targetInfo,
                adaptive: state.adaptiveState,
                liveTarget: state.liveTarget
            )
            .presentationDetents([.large])
        }
    }

    private func dateNavigation(_ state: LogViewState) -> some View {
        HStack {
            Button { state.goToPreviousDay() } label: {
                Image(systemName: "chevron.left")
            }
            Spacer()
            Text(state.selectedDateLabel)
                .font(.headline)
            Spacer()
            Button { state.goToNextDay() } label: {
                Image(systemName: "chevron.right")
            }
            .disabled(state.isToday)
        }
        .buttonStyle(.borderless)
    }

    private func calorieSummary(_ state: LogViewState) -> some View {
        let consumed = Int(state.entries.reduce(0) { $0 + $1.calories }.rounded())
        return Button { showingSummary = true } label: {
            SectionCard(title: "Today's intake") {
                LabeledProgress(
                    label: "Calories",
                    caption: "\(consumed) / \(state.liveTarget) kcal",
                    value: Double(consumed),
                    total: Double(max(state.liveTarget, 1))
                )
            }
        }
        .buttonStyle(.plain)
    }

    private func macroSummary(_ state: LogViewState) -> some View {
        let hasTargets = state.targetInfo.macros.proteinG > 0
        let protein = Int(state.entries.reduce(0) { $0 + $1.protein }.rounded())
        let carbs = Int(state.entries.reduce(0) { $0 + $1.carbohydrates }.rounded())
        let fat = Int(state.entries.reduce(0) { $0 + $1.fat }.rounded())

        return Button { showingSummary = true } label: {
            SectionCard(title: "Macros") {
                if hasTargets {
                    VStack(spacing: 12) {
                        LabeledProgress(label: "Protein", caption: "\(protein)g of \(state.targetInfo.macros.proteinG)g", value: Double(protein), total: Double(state.targetInfo.macros.proteinG))
                        LabeledProgress(label: "Carbs", caption: "\(carbs)g of \(state.targetInfo.macros.carbsG)g", value: Double(carbs), total: Double(state.targetInfo.macros.carbsG))
                        LabeledProgress(label: "Fat", caption: "\(fat)g of \(state.targetInfo.macros.fatG)g", value: Double(fat), total: Double(state.targetInfo.macros.fatG))
                    }
                } else {
                    HStack {
                        Text("Protein \(state.entries.isEmpty ? "—" : "\(protein)g")")
                        Spacer()
                        Text("Carbs \(state.entries.isEmpty ? "—" : "\(carbs)g")")
                        Spacer()
                        Text("Fat \(state.entries.isEmpty ? "—" : "\(fat)g")")
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func waterSection(_ state: LogViewState) -> some View {
        SectionCard(title: "Water today") {
            VStack(alignment: .leading, spacing: 12) {
                Text("\(state.water.reduce(0) { $0 + $1.amountMl }) ml")
                    .font(.title2.bold())

                HStack(spacing: 8) {
                    ForEach(container.profileStore.waterQuickAddAmountsMl, id: \.self) { amount in
                        // Amounts are user-configurable (Settings ▸ Water
                        // amounts) and can run to 4 digits, so the label needs
                        // to shrink to fit rather than wrap mid-word onto a
                        // second line inside the button.
                        Button("+\(amount)ml") { state.addWater(amount) }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                            .font(.footnote)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    Spacer(minLength: 4)
                    if !state.water.isEmpty {
                        Button("Undo last entry") { state.removeLastWater() }
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func workoutsSection(_ state: LogViewState) -> some View {
        if !state.workoutsToday.isEmpty {
            SectionCard(title: "Today's workouts", subtitle: "From Apple Watch / Fitness — already counted in \"Activity today\" above.") {
                VStack(spacing: 10) {
                    ForEach(state.workoutsToday) { workout in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(workout.activityName)
                                    .font(.subheadline.weight(.medium))
                                Text(workout.start, style: .time)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text("\(Int(workout.kcal.rounded())) kcal")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func adaptiveCard(_ state: LogViewState) -> some View {
        SectionCard(title: "Adaptive targets") {
            switch state.adaptiveState {
            case .disconnected:
                Button {
                    Task {
                        try? await container.healthManager.requestAuthorization()
                        state.reload()
                    }
                } label: {
                    Text("Connect Apple Health to enable adaptive targets.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)

            case .active(let a):
                VStack(alignment: .leading, spacing: 10) {
                    statusChip(for: a.trendStatus)

                    breakdownRow("Base target", "\(state.targetInfo.caloriesKcal) kcal")
                    breakdownRow("Activity today", a.activeKcal > 0 ? "+\(Int(a.activeKcal.rounded())) kcal" : "0 kcal")
                    breakdownRow(
                        "Previous day balance",
                        state.targetInfo.bleedthroughKcal >= 0
                            ? "+\(state.targetInfo.bleedthroughKcal) kcal"
                            : "-\(-state.targetInfo.bleedthroughKcal) kcal"
                    )
                    breakdownRow("Weight trend", trendText(a))
                    Divider()
                    breakdownRow("Today's target", "\(state.liveTarget) kcal", emphasized: true)

                    if a.trendStatus == .noData {
                        Text("Add weight readings in Health to enable trend-based fine-tuning.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private func statusChip(_ text: String, tint: Color) -> some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .overlay(Capsule().stroke(tint, lineWidth: 1))
            .foregroundStyle(tint)
    }

    private func statusChip(for status: LogViewState.AdaptiveState.TrendStatus) -> some View {
        switch status {
        case .onTrack: return statusChip("On track", tint: .accentColor)
        case .noData: return statusChip("Active", tint: .secondary)
        case .tooFast, .tooSlow: return statusChip("Adjusting", tint: .orange)
        }
    }

    private func trendText(_ a: LogViewState.AdaptiveState.Active) -> String {
        if a.trendStatus == .noData { return "Collecting data…" }
        let sign = a.trendAdjustment >= 0 ? "+\(a.trendAdjustment)" : "\(a.trendAdjustment)"
        if let rate = a.weeklyRateKgPerWeek {
            return String(format: "%@ kcal (%.2f kg/wk)", sign, rate)
        }
        return "\(a.trendAdjustment) kcal"
    }

    private func breakdownRow(_ label: String, _ value: String, emphasized: Bool = false) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value)
        }
        .font(emphasized ? .subheadline.weight(.semibold) : .subheadline)
        .foregroundStyle(emphasized ? .primary : .secondary)
    }

    private func entriesSection(_ state: LogViewState) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Today's meals")
                .font(.headline)

            if state.entries.isEmpty {
                Text("No entries yet — scan or search a food to get started.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(state.entries) { entry in
                    LogEntryRow(
                        entry: entry,
                        onFix: { container.fixTarget = .logged(entry) },
                        onRemove: { state.deleteEntry(entry) }
                    )
                }
            }
        }
    }
}

private struct LogEntryRow: View {
    let entry: LogEntry
    let onFix: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.foodName)
                    .font(.subheadline.weight(.medium))
                Text("\(Int(entry.calories.rounded())) kcal • \(Int(entry.protein.rounded()))g P • \(Int(entry.carbohydrates.rounded()))g C • \(Int(entry.fat.rounded()))g F")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Button("Fix", action: onFix)
                .font(.caption)
                .buttonStyle(.bordered)
            Button(role: .destructive, action: onRemove) {
                Image(systemName: "trash")
            }
            .buttonStyle(.borderless)
        }
        .padding(12)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
