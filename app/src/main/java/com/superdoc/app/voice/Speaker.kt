package com.superdoc.app.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.superdoc.app.util.Script
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** "Read aloud" using the phone's text-to-speech. The voice is picked from the text: Hindi text gets a Hindi voice. */
@Singleton
class Speaker @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Result { STARTED, NOT_READY, LANGUAGE_MISSING }

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private var ready = false
    private var pending: String? = null
    private var tts: TextToSpeech? = null

    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setSpeechRate(0.85f)
                pending?.let { speak(it) }
            }
            pending = null
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _speaking.value = true }
                override fun onDone(utteranceId: String?) { if (utteranceId == LAST) _speaking.value = false }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { _speaking.value = false }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { _speaking.value = false }
            })
        }
    }

    fun speak(text: String): Result {
        ensure()
        val engine = tts
        if (!ready || engine == null) {
            pending = text
            return Result.NOT_READY
        }
        val locale: Locale = Script.localeFor(text)
        val available = engine.setLanguage(locale)
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
            return Result.LANGUAGE_MISSING
        }
        engine.stop()
        val chunks = chunk(text, TextToSpeech.getMaxSpeechInputLength() - 100)
        chunks.forEachIndexed { i, part ->
            val id = if (i == chunks.lastIndex) LAST else "part$i"
            engine.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        _speaking.value = true
        return Result.STARTED
    }

    fun stop() {
        tts?.stop()
        _speaking.value = false
    }

    fun installVoiceDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun chunk(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val parts = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            val cut = rest.lastIndexOfAny(charArrayOf('\n', '।', '.', '?', '!'), max).takeIf { it > max / 2 } ?: max
            parts += rest.substring(0, cut + 1)
            rest = rest.substring(cut + 1)
        }
        if (rest.isNotBlank()) parts += rest
        return parts
    }

    private companion object {
        const val LAST = "last"
    }
}
