package com.superdoc.app.help

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the recorded help narration bundled in assets. Recorded voices are used instead of the
 * phone's text-to-speech because Hindi voices are missing or poor on many budget phones.
 */
@Singleton
class HelpAudio @Inject constructor(@ApplicationContext private val context: Context) {
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    /** Returns false when the clip is not bundled, so the caller can fall back to text-to-speech. */
    fun play(path: String): Boolean {
        stop()
        return runCatching {
            val mp = MediaPlayer()
            context.assets.openFd(path).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setOnCompletionListener { stop() }
            mp.prepare()
            mp.start()
            player = mp
            _playing.value = true
        }.isSuccess
    }

    fun stop() {
        player?.runCatching { if (isPlaying) stop(); release() }
        player = null
        _playing.value = false
    }
}
