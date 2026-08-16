import SwiftUI
import ScaScanKit

/// The voice food-logging screen — reachable from Home's "Log by voice" card,
/// or opened straight into listening by the Control Center action (see
/// `ScascanApp`'s pending-voice-request check on foreground). Unlike
/// Camera/Barcode/Search, this doesn't show a confirmation sheet: the result
/// is added to the log immediately, with a brief "Added — Undo" banner as the
/// safety net (`AppContainer.pendingUndo`, rendered in `MainTabView`) — the
/// whole point is not having to touch the phone again once you've said what
/// you ate.
struct VoiceSearchView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss
    @State private var controller = VoiceLogController()
    @State private var phase: Phase = .listening
    @State private var pulse = false

    private enum Phase: Equatable {
        case listening
        case thinking
        case error(String)
    }

    var body: some View {
        VStack(spacing: 28) {
            Spacer()

            switch phase {
            case .listening: listeningIcon
            case .thinking: thinkingContent
            case .error(let message): errorContent(message)
            }

            if phase == .listening {
                Text(controller.transcript.isEmpty ? String(localized: "Say what you're eating…") : controller.transcript)
                    .font(.title3)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(controller.transcript.isEmpty ? .secondary : .primary)
                    .padding(.horizontal, 24)
                    .frame(minHeight: 60)
                    .animation(.default, value: controller.transcript)
            }

            Spacer()
            controls
        }
        .padding(20)
        // Uses the standard system back button (Liquid Glass, matching every
        // other pushed screen in the app) instead of a custom one — unlike
        // Camera/Barcode, this screen has no edge-to-edge live preview under
        // it that a translucent system nav bar would clash with.
        .navigationTitle("Log by Voice")
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(phase == .thinking)
        .task {
            controller.onFinished = { handleTranscript($0) }
            await controller.requestPermissionAndStart()
            switch controller.status {
            case .listening, .notDetermined:
                break
            case .denied:
                phase = .error(String(localized: "Microphone and Speech Recognition access are needed for voice logging — enable them in Settings ▸ ScaScan."))
            case .unavailable:
                phase = .error(String(localized: "Voice recognition isn't available right now on this device."))
            }
        }
        .onDisappear { controller.cancel() }
    }

    private var listeningIcon: some View {
        ZStack {
            Circle()
                .fill(Color.scascanBrand.opacity(0.15))
                .frame(width: 160, height: 160)
                .scaleEffect(pulse ? 1.15 : 0.9)
            Circle()
                .fill(Color.scascanBrand.opacity(0.25))
                .frame(width: 120, height: 120)
                .scaleEffect(pulse ? 1.08 : 0.95)
            Image(systemName: "mic.fill")
                .font(.system(size: 40))
                .foregroundStyle(Color.scascanBrand)
                .frame(width: 88, height: 88)
                .background(.background, in: Circle())
        }
        .onAppear {
            pulse = false
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
    }

    private var thinkingContent: some View {
        VStack(spacing: 12) {
            ProgressView().controlSize(.large)
            Text("Adding to your log…").foregroundStyle(.secondary)
        }
    }

    private func errorContent(_ message: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 36))
                .foregroundStyle(.orange)
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 24)
    }

    @ViewBuilder
    private var controls: some View {
        switch phase {
        case .listening:
            Button("Done") { controller.stopListening() }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
                .disabled(controller.transcript.trimmingCharacters(in: .whitespaces).isEmpty)
        case .thinking:
            EmptyView()
        case .error:
            Button("Close") { dismiss() }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
        }
    }

    private func handleTranscript(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            dismiss()
            return
        }
        phase = .thinking
        Task {
            do {
                let facts = try await container.nutritionRepository.searchFood(trimmed)
                let entry = try container.logRepository.addEntry(facts)
                let calories = Int(facts.calories.rounded())
                container.pendingUndo = AppContainer.PendingUndo(
                    message: String(localized: "Added \(facts.foodName) · \(calories) kcal"),
                    entry: entry
                )
                dismiss()
            } catch {
                phase = .error(error.localizedDescription)
            }
        }
    }
}
