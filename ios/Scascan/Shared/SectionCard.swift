import SwiftUI

/// A rounded, elevated content group — the SwiftUI equivalent of the
/// `MaterialCardView` sections used throughout the Android layouts.
struct SectionCard<Content: View>: View {
    var title: String? = nil
    var subtitle: String? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let title {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                    if let subtitle {
                        Text(subtitle)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// A labeled linear progress bar with a "consumed / target" caption —
/// reused for calories, protein, carbs, fat, and the daily-summary sheet.
struct LabeledProgress: View {
    let label: String
    let caption: String
    let value: Double
    let total: Double
    var tint: Color = .accentColor

    private var fraction: Double {
        guard total > 0 else { return 0 }
        return min(max(value / total, 0), 1)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(label)
                    .font(.subheadline.weight(.medium))
                Spacer()
                Text(caption)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .contentTransition(.numericText())
            }
            ProgressView(value: fraction)
                .tint(tint)
        }
    }
}
