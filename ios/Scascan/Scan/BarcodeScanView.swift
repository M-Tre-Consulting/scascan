import SwiftUI
@preconcurrency import VisionKit
@preconcurrency import AVFoundation
import ScaScanKit

/// Mirrors Android's `BarcodeScanFragment` — but modernized: the original
/// just snaps a photo and has Gemini OCR the barcode number out of it.
/// VisionKit's `DataScannerViewController` decodes barcodes natively and
/// live, so a recognized code goes straight to
/// `AnalysisManager.analyzeBarcode(_:)` (the OpenFoodFacts-first path),
/// skipping the OCR round-trip entirely.
///
/// `DataScannerViewController` requires real camera hardware — it reports
/// unsupported in the iOS Simulator, where the fallback view below is shown.
struct BarcodeScanView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss

    @State private var didScan = false
    @State private var authDenied = false

    private var isSupported: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    var body: some View {
        ZStack {
            if isSupported {
                DataScannerRepresentable(
                    onScan: handleScan,
                    onPermissionDenied: { authDenied = true }
                )
                .ignoresSafeArea()
            } else {
                ContentUnavailableView(
                    "Barcode scanning unavailable",
                    systemImage: "barcode.viewfinder",
                    description: Text("This device (or the Simulator) doesn't support live barcode scanning.")
                )
            }

            VStack {
                topBar
                Spacer()
                if isSupported && !authDenied {
                    Text("Point camera at a barcode")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(.black.opacity(0.6), in: Capsule())
                        .padding(.bottom, 40)
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .alert("Camera access needed", isPresented: $authDenied) {
            Button("OK") { dismiss() }
        } message: {
            Text("Camera permission is required to scan barcodes.")
        }
    }

    private var topBar: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(.black.opacity(0.4), in: Circle())
            }
            Spacer()
        }
        .padding()
    }

    private func handleScan(_ code: String) {
        guard !didScan else { return }
        didScan = true
        container.analysisManager.analyzeBarcode(code)
        dismiss()
    }
}

private struct DataScannerRepresentable: UIViewControllerRepresentable {
    let onScan: (String) -> Void
    let onPermissionDenied: () -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controller = DataScannerViewController(
            recognizedDataTypes: [.barcode()],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: true,
            isHighlightingEnabled: true
        )
        controller.delegate = context.coordinator
        Task {
            do {
                try await requestCameraAccessIfNeeded()
                try controller.startScanning()
            } catch {
                onPermissionDenied()
            }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: DataScannerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onScan: onScan) }

    private func requestCameraAccessIfNeeded() async throws {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            return
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            if !granted { throw CameraCaptureError.noImageData }
        default:
            throw CameraCaptureError.noImageData
        }
    }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onScan: (String) -> Void
        init(onScan: @escaping (String) -> Void) { self.onScan = onScan }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            for item in addedItems {
                if case .barcode(let barcode) = item, let payload = barcode.payloadStringValue {
                    onScan(payload)
                    return
                }
            }
        }
    }
}
