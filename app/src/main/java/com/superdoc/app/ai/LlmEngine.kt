package com.superdoc.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import com.superdoc.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Timing of one answer, for the "Test speed on this phone" screen. */
data class GenerationStats(val loadMs: Long, val firstWordMs: Long, val totalMs: Long, val outputTokens: Int) {
    val tokensPerSecond: Float get() = if (totalMs > firstWordMs) outputTokens * 1000f / (totalMs - firstWordMs) else 0f
}

class InputTooLongException : IllegalArgumentException("Text is too long for the model")

/**
 * Runs a model on the phone's CPU with MediaPipe. Nothing is sent anywhere.
 * The model stays loaded for a short while after use, then is closed to give memory back.
 */
@Singleton
class LlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var llm: LlmInference? = null
    private var loaded: String? = null
    private var idleJob: Job? = null
    @Volatile private var session: LlmInferenceSession? = null

    /** Generates an answer. [onPartial] receives the text so far, for showing progress. */
    suspend fun generate(
        model: ModelInfo,
        file: File,
        prompt: String,
        maxOutputTokens: Int,
        temperature: Float = 0.2f,
        onPartial: (String) -> Unit = {},
    ): Pair<String, GenerationStats> = mutex.withLock {
        idleJob?.cancel()
        try {
            withContext(Dispatchers.Default) {
                val loadStart = System.currentTimeMillis()
                val engine = ensureLoaded(model, file)
                val loadMs = System.currentTimeMillis() - loadStart
                val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(TOP_K)
                    .setTopP(0.9f)
                    .setTemperature(temperature)
                    .setRandomSeed(SEED)
                    .setPromptTemplates(templates(model.template))
                    .build()
                LlmInferenceSession.createFromOptions(engine, options).use { s ->
                    if (s.sizeInTokens(prompt) + maxOutputTokens > model.maxTokens) throw InputTooLongException()
                    session = s
                    s.addQueryChunk(prompt)
                    val start = System.currentTimeMillis()
                    var firstWordAt = 0L
                    val text = StringBuilder()
                    val result = suspendCancellableCoroutine { cont ->
                        val future = s.generateResponseAsync(ProgressListener { partial, done ->
                            if (partial.isNotEmpty() && firstWordAt == 0L) firstWordAt = System.currentTimeMillis()
                            text.append(partial)
                            onPartial(text.toString())
                            if (done && cont.isActive) cont.resume(text.toString())
                        })
                        future.addListener({
                            runCatching { future.get() }.exceptionOrNull()?.let { if (cont.isActive) cont.resumeWithException(it) }
                        }, Runnable::run)
                        cont.invokeOnCancellation { runCatching { s.cancelGenerateResponseAsync() } }
                    }
                    val end = System.currentTimeMillis()
                    val stats = GenerationStats(
                        loadMs = loadMs,
                        firstWordMs = (if (firstWordAt == 0L) end else firstWordAt) - start,
                        totalMs = end - start,
                        outputTokens = runCatching { s.sizeInTokens(result) }.getOrDefault(0),
                    )
                    result to stats
                }
            }
        } finally {
            session = null
            scheduleRelease()
        }
    }

    private fun ensureLoaded(model: ModelInfo, file: File): LlmInference {
        val key = file.absolutePath + model.id
        llm?.let { if (loaded == key) return it }
        closeNow()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file.absolutePath)
            .setMaxTokens(model.maxTokens)
            .setMaxTopK(TOP_K)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        return LlmInference.createFromOptions(context, options).also {
            llm = it
            loaded = key
        }
    }

    private fun templates(t: ChatTemplate): PromptTemplates = PromptTemplates.builder()
        .setUserPrefix(t.userPrefix)
        .setUserSuffix(t.userSuffix)
        .setModelPrefix(t.modelPrefix)
        .setModelSuffix(t.modelSuffix)
        .setSystemPrefix("")
        .setSystemSuffix("")
        .build()

    private fun scheduleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            mutex.withLock { closeNow() }
        }
    }

    /** Frees the model's memory now (also called when the phone is low on memory). */
    fun release() {
        if (mutex.tryLock()) {
            try { closeNow() } finally { mutex.unlock() }
        }
    }

    private fun closeNow() {
        runCatching { llm?.close() }
        llm = null
        loaded = null
    }

    private companion object {
        const val TOP_K = 40
        const val SEED = 42
        const val IDLE_RELEASE_MS = 2 * 60 * 1000L
    }
}
