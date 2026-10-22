package com.superdoc.app.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** How a model expects a conversation turn to be wrapped. */
enum class ChatTemplate(val userPrefix: String, val userSuffix: String, val modelPrefix: String, val modelSuffix: String) {
    QWEN("<|im_start|>user\n", "<|im_end|>\n", "<|im_start|>assistant\n", "<|im_end|>\n"),
    GEMMA("<start_of_turn>user\n", "<end_of_turn>\n", "<start_of_turn>model\n", "<end_of_turn>\n"),
}

data class ModelInfo(
    val id: String,
    val name: String,
    val file: String,
    /** Null when the model can't be downloaded directly (its licence must be accepted on the web first). */
    val url: String?,
    val sha256: String?,
    val sizeBytes: Long,
    val template: ChatTemplate,
    val minRamMb: Int,
    val maxTokens: Int,
    val license: String,
    /** Whether Hindi actions are offered with this model. Qwen's tokenizer garbles Devanagari under MediaPipe. */
    val hindi: Boolean = false,
) {
    val importOnly: Boolean get() = url == null || sha256 == null
}

/** The models the app knows about, from assets/ai/models.json. */
@Singleton
class ModelCatalog @Inject constructor(@ApplicationContext context: Context) {
    val models: List<ModelInfo> by lazy {
        parse(context.assets.open("ai/models.json").bufferedReader().use { it.readText() })
    }

    fun find(id: String?): ModelInfo? = models.firstOrNull { it.id == id }

    companion object {
        private val SHA256 = Regex("^[0-9a-f]{64}$")

        fun parse(json: String): List<ModelInfo> {
            val array = JSONObject(json).getJSONArray("models")
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val url = o.optString("url").takeIf { !o.isNull("url") && it.isNotBlank() }
                val sha = o.optString("sha256").takeIf { !o.isNull("sha256") && it.isNotBlank() }?.lowercase()
                require(url == null || url.startsWith("https://")) { "Model URLs must use HTTPS" }
                require(sha == null || SHA256.matches(sha)) { "Bad SHA-256 for ${o.getString("id")}" }
                ModelInfo(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    file = o.getString("file"),
                    url = url,
                    sha256 = sha,
                    sizeBytes = o.getLong("sizeBytes"),
                    template = ChatTemplate.valueOf(o.getString("template").uppercase()),
                    minRamMb = o.getInt("minRamMb"),
                    maxTokens = o.optInt("maxTokens", 1280),
                    license = o.optString("license"),
                    hindi = o.optBoolean("hindi", false),
                )
            }
        }
    }
}
