package com.superdoc.app.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs every AI action on English and Hindi samples with the installed model and writes the answers to
 * /sdcard/Android/data/com.superdoc.app/files/ai_probe.txt for a person to judge.
 * Skipped when no model has been added to the app. Run with `am instrument` so the app's data is kept.
 */
@RunWith(AndroidJUnit4::class)
class AiQualityProbe {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val samples = mapOf(
        false to "tomorow i have to go to the docter at 10 oclock. please remind me to take the reports and my glases. " +
            "also call ramesh about the electricty bill, it came very high this month",
        true to "कल सुबह दस बजे डाक्टर के पास जाना है। रिपोट और चसमा ले जाना याद दिलाना। " +
            "रमेश को बिजली के बिल के बारे मे फोन करना है, इस महीने बिल बहुत जादा आया",
    )

    @Test
    fun allActions() = runBlocking {
        val catalog = ModelCatalog(context)
        val model = catalog.find("qwen2.5-0.5b-q8")!!
        val file = File(File(context.filesDir, "models"), model.file)
        assumeTrue("Model not installed", file.exists())

        val engine = LlmEngine(context, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val report = StringBuilder()
        for ((hindi, text) in samples) {
            for (action in AiAction.entries) {
                val (raw, stats) = engine.generate(model, file, AiPrompts.build(action, text, hindi), AiPrompts.maxOutputTokens(action))
                report.append("=== ${action.name} hindi=$hindi ${stats.totalMs} ms\n")
                    .append(OutputCleaner.clean(raw, text) ?: "<empty>").append("\n\n")
            }
        }
        for (spoken in listOf("isko thoda saaf kar do", "please make this into something I can send to the bank manager")) {
            val (answer, _) = engine.generate(model, file, AiPrompts.classify(spoken), maxOutputTokens = 8, temperature = 0f)
            report.append("=== CLASSIFY '$spoken' -> '$answer' -> ${IntentMatcher.fromModelAnswer(answer)}\n")
        }
        engine.release()
        File(context.getExternalFilesDir(null), "ai_probe.txt").writeText(report.toString())
    }
}
