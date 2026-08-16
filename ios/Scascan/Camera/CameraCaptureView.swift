import SwiftUI
import ScaScanKit

/// Mirrors Android's `CameraFragment` — live CameraX-equivalent preview via
/// AVFoundation, pinch-to-zoom, and a shutter that hands the photo to the
/// shared `AnalysisManager` and pops back immediately (analysis continues in
/// the background; the result sheet appears from `MainTabView` when done).
struct CameraCaptureView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss

    @State private var controller = CameraController()
    @State private var zoomBase: CGFloat = 1
    @State private var isCapturing = false
    @State private var captureError: String?

    var body: some View {
        ZStack {
            background

            VStack {
                Spacer()
                if controller.status == .granted {
                    shutterButton
                }
            }
        }
        // Background hidden (not the whole toolbar) so the standard system
        // back button — Liquid Glass, matching every other pushed screen —
        // still shows, floating over the edge-to-edge preview instead of
        // sitting on a solid bar.
        .toolbarBackground(.hidden, for: .navigationBar)
        .task { await controller.requestPermissionAndStart() }
        .onDisappear { controller.stop() }
        .alert("Capture failed", isPresented: Binding(
            get: { captureError != nil },
            set: { if !$0 { captureError = nil } }
        )) {
            Button("OK") { captureError = nil }
        } message: {
            Text(captureError ?? "")
        }
    }

    @ViewBuilder
    private var background: some View {
        switch controller.status {
        case .granted:
            CameraPreviewView(session: controller.session)
                .ignoresSafeArea()
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in controller.setZoom(factor: zoomBase * value) }
                        .onEnded { value in zoomBase = max(zoomBase * value, 1) }
                )
        case .denied:
            ContentUnavailableView(
                "Camera access needed",
                systemImage: "camera.fill",
                description: Text("Camera permission is required to use this feature.")
            )
        case .unavailable:
            ContentUnavailableView(
                "No camera available",
                systemImage: "camera.fill",
                description: Text("This device (or the Simulator) doesn't expose a camera.")
            )
        case .notDetermined:
            Color.black.ignoresSafeArea()
        }
    }

    private var shutterButton: some View {
        Button(action: capture) {
            Circle()
                .strokeBorder(.white, lineWidth: 4)
                .frame(width: 76, height: 76)
                .overlay(
                    Circle()
                        .fill(.white)
                        .frame(width: 64, height: 64)
                        .opacity(isCapturing ? 0.4 : 1)
                )
        }
        .disabled(isCapturing)
        .padding(.bottom, 32)
    }

    private func capture() {
        isCapturing = true
        Task {
            defer { isCapturing = false }
            do {
                let image = try await controller.capturePhoto()
                container.analysisManager.analyze(image)
                dismiss()
            } catch {
                captureError = error.localizedDescription
            }
        }
    }
}
