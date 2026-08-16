import Speech
import AVFoundation
import Observation

/// Drives on-device speech-to-text for voice food logging. Mirrors
/// `CameraController`'s shape (permission gate → live session → result), but
/// for `Speech`/`AVAudioEngine` instead of `AVCaptureSession`.
///
/// Transcription happens entirely on-device via `SFSpeechRecognizer` — free,
/// fast, works offline, and lets the listening screen show what was actually
/// heard live, which matters here since the result gets auto-added to the
/// log (see `VoiceSearchView`): seeing the transcript as you speak is the
/// only chance to notice a misheard word before it becomes a logged entry.
@MainActor
@Observable
final class VoiceLogController: NSObject {
    enum Status: Equatable {
        case notDetermined
        case listening
        case denied
        /// No speech recognizer for the current locale, or the device can't
        /// currently service requests (e.g. no network for locales that
        /// require server-side recognition).
        case unavailable
    }

    private(set) var status: Status = .notDetermined
    private(set) var transcript: String = ""

    /// Fires exactly once per `start()` call, with whatever transcript was
    /// captured (possibly empty, if nothing was understood before it stopped).
    var onFinished: ((String) -> Void)?

    private let recognizer = SFSpeechRecognizer(locale: .current)
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var silenceWatchTask: Task<Void, Never>?
    private var lastTranscriptChange = Date()
    private var hasFinishedCurrentSession = true

    /// How long to wait after the transcript stops changing before treating
    /// the user as "done talking" and wrapping up automatically — long enough
    /// to survive a normal mid-sentence pause, short enough to feel hands-free.
    private static let silenceTimeout: TimeInterval = 1.8

    func requestPermissionAndStart() async {
        guard let recognizer, recognizer.isAvailable else {
            status = .unavailable
            return
        }

        let speechStatus = await withCheckedContinuation { (continuation: CheckedContinuation<SFSpeechRecognizerAuthorizationStatus, Never>) in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
        }
        guard speechStatus == .authorized else {
            status = .denied
            return
        }

        // Wrapped explicitly (not via the auto-bridged `async` overload):
        // that overload's completion doesn't reliably hop back onto the
        // MainActor on this toolchain, leaving `startListening()` below to
        // run on whatever background dispatch thread the ObjC completion
        // fired on — AVAudioSession/AVAudioEngine assert they're being
        // driven from the main thread and trap (`_dispatch_assert_queue_fail`)
        // when they're not. A manual `CheckedContinuation`, same as the
        // speech-authorization request above, makes Swift's own actor-hop
        // machinery respect the MainActor isolation this class declares.
        let micGranted = await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        guard micGranted else {
            status = .denied
            return
        }

        do {
            try startListening()
            status = .listening
        } catch {
            status = .unavailable
        }
    }

    /// Ends the session immediately (e.g. the user tapped "Done" instead of
    /// waiting for the silence timeout) and reports whatever was heard so far.
    func stopListening() {
        guard status == .listening else { return }
        finish()
    }

    /// Discards the session without reporting anything — the user backed out.
    func cancel() {
        guard status == .listening else { return }
        hasFinishedCurrentSession = true // suppress the pending onFinished callback
        teardownAudio()
    }

    private func startListening() throws {
        transcript = ""
        lastTranscriptChange = .now
        hasFinishedCurrentSession = false

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: .duckOthers)
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        req.requiresOnDeviceRecognition = false // fall back to server if on-device isn't available for this locale
        request = req

        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1_024, format: format) { [weak req] buffer, _ in
            req?.append(buffer)
        }

        audioEngine.prepare()
        try audioEngine.start()

        task = recognizer?.recognitionTask(with: req) { [weak self] result, error in
            Task { @MainActor [weak self] in
                guard let self else { return }
                if let result {
                    self.transcript = result.bestTranscription.formattedString
                    self.lastTranscriptChange = .now
                    if result.isFinal { self.finish() }
                } else if error != nil {
                    self.finish()
                }
            }
        }

        watchForSilence()
    }

    private func watchForSilence() {
        silenceWatchTask?.cancel()
        silenceWatchTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(300))
                guard let self, self.status == .listening else { return }
                let hasSpokenYet = !self.transcript.isEmpty
                let quietFor = Date().timeIntervalSince(self.lastTranscriptChange)
                if hasSpokenYet && quietFor > Self.silenceTimeout {
                    self.finish()
                    return
                }
            }
        }
    }

    private func finish() {
        guard !hasFinishedCurrentSession else { return }
        hasFinishedCurrentSession = true
        let result = transcript
        teardownAudio()
        onFinished?(result)
    }

    private func teardownAudio() {
        silenceWatchTask?.cancel()
        silenceWatchTask = nil
        if audioEngine.isRunning { audioEngine.stop() }
        audioEngine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        request = nil
        task?.cancel()
        task = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        status = .notDetermined
    }
}
