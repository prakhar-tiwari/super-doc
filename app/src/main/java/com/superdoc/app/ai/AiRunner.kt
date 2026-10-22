package com.superdoc.app.ai

import com.superdoc.app.util.Script
import javax.inject.Inject
import javax.inject.Singleton

class NoModelException : IllegalStateException("No AI model installed")
class EmptyAnswerException : IllegalStateException("The model gave no answer")
class NoHindiException : IllegalStateException("The installed model does not handle Hindi")

/** Progress of a running action: which part of the note is being worked on, and the answer so far. */
data class AiProgress(val part: Int, val parts: Int, val partial: String)

/** Runs an [AiAction] on a note: splits long notes, asks the model, and tidies the answer. */
@Singleton
class AiRunner @Inject constructor(
    private val engine: LlmEngine,
    private val models: ModelManager,
) {
    suspend fun run(action: AiAction, text: String, onProgress: (AiProgress) -> Unit = {}): String {
        val model = models.active() ?: throw NoModelException()
        val file = models.fileFor(model)
        val hindi = Script.isMostlyHindi(text)
        if (!model.hindi && (hindi || action == AiAction.TRANSLATE)) throw NoHindiException()
        val englishAnswer = hindi == (action == AiAction.TRANSLATE)
        // A letter must be written in one go; the other actions work paragraph by paragraph.
        val chunks = if (action == AiAction.MAKE_LETTER) listOf(text) else Chunker.split(text, Chunker.maxChars(hindi))
        val done = mutableListOf<String>()
        chunks.forEachIndexed { i, chunk ->
            val prompt = AiPrompts.build(action, chunk, hindi)
            val (raw, _) = engine.generate(model, file, prompt, AiPrompts.maxOutputTokens(action)) { partial ->
                onProgress(AiProgress(i + 1, chunks.size, (done + partial).joinToString("\n")))
            }
            val answer = OutputCleaner.clean(raw, chunk) ?: throw EmptyAnswerException()
            done += if (englishAnswer) OutputCleaner.sentenceCase(answer) else answer
        }
        return done.joinToString("\n")
    }

    /** Asks the model what a spoken instruction means. Used only when the keywords found nothing. */
    suspend fun understand(spoken: String): VoiceIntent {
        val model = models.active() ?: return VoiceIntent.Unknown
        val (answer, _) = engine.generate(model, models.fileFor(model), AiPrompts.classify(spoken), maxOutputTokens = 8, temperature = 0f)
        return IntentMatcher.fromModelAnswer(answer)
    }

    /** A fixed task for "Test speed on this phone": the same text every time, so results can be compared. */
    suspend fun benchmark(hindi: Boolean, onPartial: (String) -> Unit): Pair<String, GenerationStats> {
        val model = models.active() ?: throw NoModelException()
        val useHindi = hindi && model.hindi
        val sample = if (useHindi) BENCH_HI else BENCH_EN
        val (raw, stats) = engine.generate(
            model, models.fileFor(model),
            AiPrompts.build(AiAction.FIX_SPELLING, sample, useHindi),
            AiPrompts.maxOutputTokens(AiAction.FIX_SPELLING),
            onPartial = onPartial,
        )
        val answer = OutputCleaner.clean(raw, sample) ?: raw
        return (if (useHindi) answer else OutputCleaner.sentenceCase(answer)) to stats
    }

    companion object {
        const val BENCH_EN = "tomorow i have to go to the docter at 10 oclock. please remind me to take the reports and my glases."
        const val BENCH_HI = "कल सुबह दस बजे डाक्टर के पास जाना है। रिपोट और चसमा ले जाना याद दिलाना।"
    }
}
