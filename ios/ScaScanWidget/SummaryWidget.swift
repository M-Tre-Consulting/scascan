import WidgetKit
import SwiftUI
import ScaScanKit

struct SummaryEntry: TimelineEntry {
    let date: Date
    let consumed: Int
    let target: Int
    let protein: Int
    let carbs: Int
    let fat: Int
    let waterMl: Int
}

struct SummaryProvider: TimelineProvider {
    func placeholder(in context: Context) -> SummaryEntry {
        SummaryEntry(date: .now, consumed: 1200, target: 2000, protein: 80, carbs: 140, fat: 40, waterMl: 750)
    }

    func getSnapshot(in context: Context, completion: @escaping (SummaryEntry) -> Void) {
        Task { @MainActor in completion(Self.currentEntry()) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SummaryEntry>) -> Void) {
        Task { @MainActor in
            let entry = Self.currentEntry()
            // The numbers only meaningfully change as often as the user logs
            // something — 30 minutes keeps the widget fresh without wasting
            // refresh budget, matching Android's on-demand `triggerUpdate`
            // closely enough for a timeline-based (rather than push-based) API.
            let next = Calendar.current.date(byAdding: .minute, value: 30, to: .now) ?? .now.addingTimeInterval(1_800)
            completion(Timeline(entries: [entry], policy: .after(next)))
        }
    }

    @MainActor
    private static func currentEntry() -> SummaryEntry {
        let container = ScaScanSchema.makeContainer()
        let repository = LogRepository(modelContainer: container)
        let entries = (try? repository.entries(forDateOffset: 0)) ?? []
        let water = (try? repository.waterLogs(forDateOffset: 0)) ?? []
        return SummaryEntry(
            date: .now,
            consumed: Int(entries.reduce(0) { $0 + $1.calories }.rounded()),
            // Uses the profile/AI base target rather than the full adaptive
            // (HealthKit-aware) target — matches Android's own background
            // fallback path for when the widget can't reliably reach Health.
            target: repository.dailyCalorieTarget(),
            protein: Int(entries.reduce(0) { $0 + $1.protein }.rounded()),
            carbs: Int(entries.reduce(0) { $0 + $1.carbohydrates }.rounded()),
            fat: Int(entries.reduce(0) { $0 + $1.fat }.rounded()),
            waterMl: water.reduce(0) { $0 + $1.amountMl }
        )
    }
}

struct SummaryWidgetView: View {
    let entry: SummaryEntry

    private var fraction: Double {
        entry.target > 0 ? min(Double(entry.consumed) / Double(entry.target), 1) : 0
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text("\(entry.consumed)")
                    .font(.title2.bold())
                Text("/ \(entry.target) kcal")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ProgressView(value: fraction)
                .tint(.accentColor)

            Text("P \(entry.protein)g · C \(entry.carbs)g · F \(entry.fat)g")
                .font(.caption2)
                .foregroundStyle(.secondary)

            Spacer(minLength: 2)

            HStack {
                Label("\(entry.waterMl) ml", systemImage: "drop.fill")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Spacer()
                Button(intent: AddWaterIntent()) {
                    Image(systemName: "plus.circle.fill")
                        .font(.title3)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
    }
}

struct SummaryWidget: Widget {
    let kind = "SummaryWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SummaryProvider()) { entry in
            SummaryWidgetView(entry: entry)
                .containerBackground(.background, for: .widget)
        }
        .configurationDisplayName("Daily Summary")
        .description("Track today's calories, macros, and water at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

#Preview(as: .systemSmall) {
    SummaryWidget()
} timeline: {
    SummaryEntry(date: .now, consumed: 1200, target: 2000, protein: 80, carbs: 140, fat: 40, waterMl: 750)
}
