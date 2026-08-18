package com.scascan.app.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject

sealed class VoiceState {
    object Listening : VoiceState()
    data class PartialResult(val text: String) : VoiceState()
    data class FinalResult(val text: String) : VoiceState()
    data class Error(val reason: String) : VoiceState()
}

/**
 * Wraps Android's [SpeechRecognizer] for free-text voice logging. Prefers on-device recognition
 * but does not require it — unlike iOS, on-device availability varies by device/language pack on
 * Android, so this falls back to online recognition rather than failing outright (see
 * ios/ARCHITECTURE.md §9 for the iOS behavior this approximates).
 */
class VoiceRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Must be collected from the main thread. Completes after a final result or an error. */
    fun listen(): Flow<VoiceState> = callbackFlow {
        if (!isAvailable()) {
            trySend(VoiceState.Error(REASON_UNAVAILABLE))
            close()
            return@callbackFlow
        }

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r

        // The on-device model can pass its live partial-result confidence check but then fail
        // its stricter final-confidence check (ERROR_NO_MATCH) for the same utterance — so a
        // "no match" error with a good partial transcript already in hand is treated as success
        // instead of a false "didn't understand".
        var lastPartial = ""

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceState.Listening)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                trySend(VoiceState.FinalResult(text.ifBlank { lastPartial }))
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    lastPartial = text
                    trySend(VoiceState.PartialResult(text))
                }
            }

            override fun onError(error: Int) {
                val isNoMatch = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (isNoMatch && lastPartial.isNotBlank()) {
                    trySend(VoiceState.FinalResult(lastPartial))
                    close()
                    return
                }
                val reason = if (isNoMatch) REASON_NO_MATCH else REASON_GENERIC
                trySend(VoiceState.Error(reason))
                close()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Preference, not a requirement — falls back to online recognition when unavailable.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
        }
        r.startListening(intent)

        awaitClose {
            r.setRecognitionListener(null)
            r.destroy()
            recognizer = null
        }
    }

    fun cancel() {
        recognizer?.stopListening()
    }

    companion object {
        private const val SILENCE_TIMEOUT_MS = 1800
        const val REASON_UNAVAILABLE = "unavailable"
        const val REASON_NO_MATCH = "no_match"
        const val REASON_GENERIC = "generic"
    }
}
