import SwiftUI
import ScaScanKit

/// Mirrors Android's `MainFragment` (ViewPager2 shell) + `MainTabsAdapter`.
/// `TabView` gives swipeable paging and the tab bar natively, replacing both
/// the custom animated bottom-nav view and the ViewPager2 wiring in one shot.
struct MainTabView: View {
    @Environment(AppContainer.self) private var container
    @State private var selection = 0
    @State private var path: [Route] = []

    var body: some View {
        TabView(selection: $selection) {
            Tab("Scan", systemImage: "house.fill", value: 0) {
                NavigationStack(path: $path) {
                    HomeView(path: $path)
                        .navigationDestination(for: Route.self) { destination(for: $0) }
                }
            }
            Tab("Log", systemImage: "fork.knife", value: 1) {
                NavigationStack {
                    LogView()
                }
            }
            Tab("Profile", systemImage: "person.crop.circle", value: 2) {
                NavigationStack {
                    ProfileView()
                }
            }
        }
        .overlay(alignment: .bottom) {
            if container.analysisManager.state == .processing {
                AnalysisProgressBar()
            }
        }
        .sheet(item: analysisResultBinding) { facts in
            AnalysisResultSheet(
                facts: facts,
                onAdd: {
                    try? container.logRepository.addEntry(facts)
                    container.analysisManager.dismiss()
                },
                onFix: { container.fixTarget = .pending(facts) },
                onViewDetails: {
                    container.analysisManager.dismiss()
                    path.append(.result(facts))
                    selection = 0
                },
                onDismiss: { container.analysisManager.dismiss() }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(item: fixTargetBinding) { target in
            FixEntrySheet(target: target) { correction in
                switch target {
                case .pending(let facts):
                    container.analysisManager.fixPending(facts, correction: correction)
                case .logged(let entry):
                    fixLoggedEntry(entry, correction: correction)
                }
                container.fixTarget = nil
            }
            .presentationDetents([.medium])
        }
        .onChange(of: container.deepLinkTab) { _, tab in
            guard let tab else { return }
            selection = tab
            container.deepLinkTab = nil
        }
        .onChange(of: container.deepLinkRoute) { _, route in
            guard let route else { return }
            path.append(route)
            container.deepLinkRoute = nil
        }
    }

    /// Bridges `AnalysisManager.State.complete` to a `.sheet(item:)` binding.
    private var analysisResultBinding: Binding<NutritionFacts?> {
        Binding(
            get: {
                if case .complete(let facts) = container.analysisManager.state { return facts }
                return nil
            },
            set: { if $0 == nil { container.analysisManager.dismiss() } }
        )
    }

    private var fixTargetBinding: Binding<FixEntryTarget?> {
        Binding(get: { container.fixTarget }, set: { container.fixTarget = $0 })
    }

    private func fixLoggedEntry(_ entry: LogEntry, correction: String) {
        Task {
            do {
                let facts = try await container.nutritionRepository.fixEntry(
                    foodName: entry.foodName, servingSize: entry.servingSize, correction: correction
                )
                entry.foodName = facts.foodName
                entry.servingSize = facts.servingSize
                entry.calories = facts.calories
                entry.protein = facts.protein
                entry.carbohydrates = facts.carbohydrates
                entry.fat = facts.fat
                entry.fiber = facts.fiber
                entry.sugar = facts.sugar
                entry.sodium = facts.sodium
                try container.logRepository.updateEntry(entry)
            } catch {
                // Surfaced via LogView's own error handling in a future pass.
            }
        }
    }

    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .camera: CameraCaptureView()
        case .barcode: BarcodeScanView()
        case .search: SearchView()
        case .result(let facts): NutritionResultView(facts: facts)
        case .settings: AppSettingsView()
        }
    }
}

private struct AnalysisProgressBar: View {
    var body: some View {
        HStack(spacing: 10) {
            ProgressView()
            Text("Analyzing…")
                .font(.subheadline)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.regularMaterial, in: Capsule())
        .padding(.bottom, 8)
    }
}

extension NutritionFacts: @retroactive Identifiable {
    public var id: String { foodName + servingSize }
}
