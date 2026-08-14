import SwiftUI

/// Placeholder — real implementation (VisionKit `DataScannerViewController`,
/// mirroring Android's `BarcodeScanFragment`/ML Kit) lands in Phase 3.
struct BarcodeScanView: View {
    var body: some View {
        ContentUnavailableView(
            "Barcode scan — coming in Phase 3",
            systemImage: "barcode.viewfinder",
            description: Text("VisionKit's native barcode scanner will decode here and feed straight into AnalysisManager.analyzeBarcode(_:).")
        )
        .navigationTitle("Scan Barcode")
        .navigationBarTitleDisplayMode(.inline)
    }
}
