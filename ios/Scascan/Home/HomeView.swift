import SwiftUI

/// Mirrors Android's `HomeFragment` — the portal with three scan choices.
struct HomeView: View {
    @Binding var path: [Route]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("ScaScan")
                        .font(.largeTitle.bold())
                    Text("AI-powered nutritional analysis")
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 8)

                ScanChoiceCard(
                    icon: "camera.fill",
                    title: "Take a Photo",
                    subtitle: "Identify food from any image"
                ) { path.append(.camera) }

                ScanChoiceCard(
                    icon: "barcode.viewfinder",
                    title: "Scan Barcode",
                    subtitle: "Look up any product by barcode"
                ) { path.append(.barcode) }

                ScanChoiceCard(
                    icon: "magnifyingglass",
                    title: "Search Food",
                    subtitle: "Type any food or meal description"
                ) { path.append(.search) }
            }
            .padding(20)
        }
        .navigationTitle("ScaScan")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ScanChoiceCard: View {
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void

    @State private var tapCount = 0

    var body: some View {
        Button {
            tapCount += 1
            action()
        } label: {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.title2)
                    .frame(width: 44, height: 44)
                    .background(.tint.opacity(0.15), in: Circle())
                    .foregroundStyle(.tint)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.tertiary)
                    .font(.footnote.weight(.semibold))
            }
            .padding(16)
            .background(.background.secondary, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: tapCount)
    }
}
