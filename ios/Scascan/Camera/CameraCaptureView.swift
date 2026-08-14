import SwiftUI

/// Placeholder — real implementation (AVFoundation live preview + shutter,
/// mirroring Android's `CameraFragment`/`CameraX`) lands in Phase 3.
struct CameraCaptureView: View {
    var body: some View {
        ContentUnavailableView(
            "Camera capture — coming in Phase 3",
            systemImage: "camera.fill",
            description: Text("Live AVFoundation preview and shutter will land here, wired to the same AnalysisManager already driving Search.")
        )
        .navigationTitle("Take Photo")
        .navigationBarTitleDisplayMode(.inline)
    }
}
