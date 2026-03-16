package com.combadge.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Wraps Android's [SpeechRecognizer] for single-shot speech recognition.
 *
 * Must be created and used on the main thread (SpeechRecognizer requirement).
 * Emits [SpeechEvent]s via a SharedFlow.
 */
class SpeechManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechManager"
        /** Silence timeout in ms before auto-stopping recognition */
        private const val SILENCE_TIMEOUT_MS = 5000
    }

    sealed class SpeechEvent {
        data class PartialResult(val text: String) : SpeechEvent()
        data class FinalResult(val text: String) : SpeechEvent()
        data class Error(val code: Int, val message: String) : SpeechEvent()
        data object EndOfSpeech : SpeechEvent()
    }

    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SpeechEvent> = _events

    private var recognizer: SpeechRecognizer? = null
    @Volatile private var isListening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Start listening. Emits [SpeechEvent]s until a final result or error.
     * Must be called on the main thread.
     */
    fun startListening() {
        if (isListening) return
        if (!isAvailable()) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            _events.tryEmit(SpeechEvent.Error(-1, "Speech recognition not available"))
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS.toLong())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS.toLong())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        isListening = true
        recognizer?.startListening(intent)
        Log.d(TAG, "Speech recognition started")
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        recognizer?.stopListening()
        Log.d(TAG, "Speech recognition stopped")
    }

    fun destroy() {
        isListening = false
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            _events.tryEmit(SpeechEvent.EndOfSpeech)
        }

        override fun onError(error: Int) {
            isListening = false
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error $error"
            }
            Log.w(TAG, "Recognition error: $msg ($error)")
            _events.tryEmit(SpeechEvent.Error(error, msg))
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull() ?: return
            Log.d(TAG, "Final result: $best")
            _events.tryEmit(SpeechEvent.FinalResult(best))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            _events.tryEmit(SpeechEvent.PartialResult(partial))
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
