package com.superdoc.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.superdoc.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Voice typing on top of Android's SpeechRecognizer.
 *
 * Order of preference (see docs/research/01-voice-typing.md):
 *  1. The on-device recognizer (Android 12+), which never sends audio off the phone.
 *  2. The default recognizer with EXTRA_PREFER_OFFLINE, which uses the downloaded
 *     offline language pack when the phone has one.
 * When "offline only" is on, step 2 still asks for offline recognition and network
 * errors are reported as "voice pack missing" rather than silently going online.
 *
 * Must be used from the main thread.
 */
class VoiceTyping(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data class Listening(val partial: String) : State
        data class Error(val messageRes: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false

    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) || onDeviceAvailable()

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(languageTag: String, offlineOnly: Boolean, onFinal: (String) -> Unit) {
        stop()
        startInternal(languageTag, offlineOnly, onFinal, preferOnDevice = true)
    }

    private fun startInternal(languageTag: String, offlineOnly: Boolean, onFinal: (String) -> Unit, preferOnDevice: Boolean) {
        usingOnDevice = preferOnDevice && onDeviceAvailable()
        val created = if (usingOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            _state.value = State.Error(R.string.voice_error_not_available)
            return
        }
        recognizer = created
        val intent = recognizerIntent(languageTag, offlineOnly)
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = State.Listening("")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = State.Listening(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                release()
                if (text.isBlank()) {
                    _state.value = State.Error(R.string.voice_error_no_match)
                } else {
                    _state.value = State.Idle
                    onFinal(text)
                }
            }

            override fun onError(error: Int) {
                release()
                val languageMissing = error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE
                if (usingOnDevice && languageMissing) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        runCatching {
                            SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                                triggerModelDownload(intent)
                                destroy()
                            }
                        }
                    }
                    // Fall back to the default recognizer, which still prefers the offline pack.
                    startInternal(languageTag, offlineOnly, onFinal, preferOnDevice = false)
                    return
                }
                _state.value = State.Error(messageFor(error, offlineOnly))
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        _state.value = State.Listening("")
        created.startListening(intent)
    }

    private fun recognizerIntent(languageTag: String, offlineOnly: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offlineOnly)
            // Older users often pause between phrases; give them time before stopping.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }

    private fun messageFor(error: Int, offlineOnly: Boolean): Int = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_error_no_match
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_error_permission
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_error_busy
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER, ERROR_SERVER_DISCONNECTED,
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
            if (offlineOnly) R.string.voice_error_offline_pack else R.string.voice_error_network
        else -> R.string.voice_error_generic
    }

    fun stop() {
        recognizer?.let {
            it.stopListening()
            it.cancel()
        }
        release()
        if (_state.value is State.Listening) _state.value = State.Idle
    }

    fun clearError() {
        if (_state.value is State.Error) _state.value = State.Idle
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        // Constants added in Android 12; values copied so the code compiles against all levels.
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
