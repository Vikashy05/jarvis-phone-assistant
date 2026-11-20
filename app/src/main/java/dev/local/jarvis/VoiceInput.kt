package dev.local.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal class VoiceInput(
    private val context: Context,
    private val showStatus: (String) -> Unit,
    private val accept: (String) -> Unit
) {
    private var current: SpeechRecognizer? = null
    val active get() = current != null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        cancel()
        showStatus("Listening timed out. Tap Speak to try again.")
    }

    fun start() {
        if (active) return
        try {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                showStatus("Offline speech is unavailable on this phone. Type a command below.")
                return
            }
            val session = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            current = session
            session.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (current === session) showStatus("Listening…")
                }

                override fun onEndOfSpeech() {
                    if (current === session) showStatus("Recognizing…")
                }

                override fun onResults(results: Bundle?) {
                    if (current !== session) return
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    cancel()
                    if (phrase.isNullOrBlank()) showStatus("No words recognized. Try again.") else accept(phrase)
                }

                override fun onError(error: Int) {
                    if (current !== session) return
                    cancel()
                    showStatus(when (error) {
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            "Install English (US) offline speech data in your speech provider, or type below."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow microphone access to use voice."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No command heard. Tap Speak to retry."
                        else -> "Offline speech stopped (code $error). Retry or type a command."
                    })
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            showStatus("Starting microphone…")
            session.startListening(request)
            handler.postDelayed(timeout, 20_000)
        } catch (failure: RuntimeException) {
            cancel()
            showStatus("Could not start offline speech. Check microphone access or type below.")
        }
    }

    fun cancel() {
        handler.removeCallbacks(timeout)
        val session = current ?: return
        current = null
        try {
            session.cancel()
        } finally {
            session.destroy()
        }
    }
}
