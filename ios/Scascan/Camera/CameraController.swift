@preconcurrency import AVFoundation
import UIKit
import Observation

/// Owns a live `AVCaptureSession` — the native replacement for Android's
/// CameraX `ProcessCameraProvider` binding in `CameraFragment`/`BarcodeScanFragment`.
/// Photo capture is wrapped in `async/await` over `AVCapturePhotoCaptureDelegate`'s
/// callback, matching this app's Swift Concurrency-first style.
@MainActor
@Observable
final class CameraController: NSObject {
    enum Status: Equatable {
        case notDetermined
        case granted
        case denied
        /// No camera hardware found (e.g. running in the iOS Simulator).
        case unavailable
    }

    private(set) var status: Status = .notDetermined

    // AVCaptureSession/AVCaptureDevice must be configured off the main thread
    // per Apple's own AVFoundation guidance, which predates Sendable checking.
    // `nonisolated(unsafe)` documents the manual contract: these are only ever
    // mutated from `sessionQueue`, except `videoZoomFactor`, which
    // `AVCaptureDevice.lockForConfiguration()` explicitly makes safe to call
    // from any thread (that lock exists precisely for this case).
    @ObservationIgnored nonisolated(unsafe) let session = AVCaptureSession()
    @ObservationIgnored nonisolated(unsafe) private let photoOutput = AVCapturePhotoOutput()
    @ObservationIgnored nonisolated(unsafe) private var device: AVCaptureDevice?

    @ObservationIgnored private let sessionQueue = DispatchQueue(label: "com.scascan.app.camera.session")
    @ObservationIgnored private var photoContinuation: CheckedContinuation<UIImage, Error>?

    func requestPermissionAndStart() async {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            await configureAndStart()
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            if granted {
                await configureAndStart()
            } else {
                status = .denied
            }
        default:
            status = .denied
        }
    }

    private func configureAndStart() async {
        let configured = await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            sessionQueue.async { [weak self] in
                guard let self else { continuation.resume(returning: false); return }
                let ok = self.configureSessionOnQueue()
                if ok { self.session.startRunning() }
                continuation.resume(returning: ok)
            }
        }
        status = configured ? .granted : .unavailable
    }

    /// Must only be called from `sessionQueue`.
    private nonisolated func configureSessionOnQueue() -> Bool {
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        session.sessionPreset = .photo

        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device) else {
            return false
        }
        self.device = device

        guard session.canAddInput(input) else { return false }
        session.addInput(input)

        guard session.canAddOutput(photoOutput) else { return false }
        session.addOutput(photoOutput)

        return true
    }

    nonisolated func setZoom(factor: CGFloat) {
        guard let device else { return }
        let clamped = min(max(factor, device.minAvailableVideoZoomFactor), device.maxAvailableVideoZoomFactor)
        try? device.lockForConfiguration()
        device.videoZoomFactor = clamped
        device.unlockForConfiguration()
    }

    func capturePhoto() async throws -> UIImage {
        try await withCheckedThrowingContinuation { continuation in
            photoContinuation = continuation
            photoOutput.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
        }
    }

    nonisolated func stop() {
        sessionQueue.async { [session] in
            session.stopRunning()
        }
    }
}

extension CameraController: AVCapturePhotoCaptureDelegate {
    nonisolated func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        // Extract the (Sendable) raw data here, synchronously — AVCapturePhoto
        // itself isn't Sendable, so it can't cross into the @MainActor Task below.
        let data = photo.fileDataRepresentation()
        Task { @MainActor in
            defer { photoContinuation = nil }
            if let error {
                photoContinuation?.resume(throwing: error)
            } else if let data, let image = UIImage(data: data) {
                photoContinuation?.resume(returning: image)
            } else {
                photoContinuation?.resume(throwing: CameraCaptureError.noImageData)
            }
        }
    }
}

enum CameraCaptureError: Error, LocalizedError {
    case noImageData
    var errorDescription: String? { "Capture failed — no image data returned." }
}
