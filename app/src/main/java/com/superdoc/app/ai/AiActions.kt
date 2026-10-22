package com.superdoc.app.ai

/** The fixed things the AI helper can do. There is no open chat: each button does one clear job. */
enum class AiAction { FIX_SPELLING, MAKE_NEAT, MAKE_LETTER, MAKE_SHORTER, TRANSLATE }

object AiPrompts {
    private const val HINDI = "Hindi (Devanagari script)"
    private const val ENGLISH = "English"

    /** [hindi] says which language the text is in; the answer stays in it (except for Translate). */
    fun build(action: AiAction, text: String, hindi: Boolean): String {
        val lang = if (hindi) HINDI else ENGLISH
        val task = when (action) {
            AiAction.FIX_SPELLING ->
                "Correct the spelling, grammar and capital letters in the text below. Keep it in $lang. " +
                    "Keep the same meaning and the same lines. Do not add anything new."
            AiAction.MAKE_NEAT ->
                "Rewrite the text below neatly in $lang: correct mistakes, use full sentences with proper punctuation, " +
                    "and put each separate point on its own line. Do not add new information."
            AiAction.MAKE_LETTER ->
                "Turn the text below into a short, polite formal letter in $lang. Begin with a greeting, " +
                    "state the request clearly, and end with a polite closing. Use only the facts in the text: " +
                    "do not add reasons, events, names, addresses or dates. Write [Name] where a name is needed."
            AiAction.MAKE_SHORTER ->
                "Make the text below shorter in $lang. Keep every important fact: names, numbers, dates and amounts."
            AiAction.TRANSLATE -> {
                val target = if (hindi) ENGLISH else HINDI
                "Translate the text below from $lang to $target. Keep names and numbers unchanged."
            }
        }
        return "$task Reply with only the result, without any explanation.\n\nText:\n$text"
    }

    /** Asks the model which action a spoken instruction means, when the keywords didn't match. */
    fun classify(spoken: String): String =
        "A user of a notes app said: \"$spoken\"\n" +
            "Which one of these does the user want? Answer with exactly one word from this list: " +
            "FIX, NEAT, LETTER, SHORTER, TRANSLATE, PDF, WORD, READ, SEND, NONE."

    /** Output budget: enough room for the answer without letting a confused model ramble on. */
    fun maxOutputTokens(action: AiAction): Int = when (action) {
        AiAction.MAKE_SHORTER -> 256
        AiAction.MAKE_LETTER -> 512
        else -> 448
    }
}

/** Long notes are processed a few paragraphs at a time so they fit in the model's memory. */
object Chunker {
    fun maxChars(hindi: Boolean): Int = if (hindi) 350 else 900

    fun split(text: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) result += current.toString().trim('\n')
            current.clear()
        }
        for (piece in pieces(text, maxChars)) {
            if (current.isNotEmpty() && current.length + piece.length + 1 > maxChars) flush()
            if (current.isNotEmpty()) current.append('\n')
            current.append(piece)
        }
        flush()
        return result.ifEmpty { listOf(text) }
    }

    /** Lines, with any line that is too long cut at sentence ends (or spaces as a last resort). */
    private fun pieces(text: String, maxChars: Int): List<String> = text.lines().flatMap { line ->
        if (line.length <= maxChars) listOf(line) else {
            val sentences = line.split(Regex("(?<=[.!?।])\\s+"))
            sentences.flatMap { s -> if (s.length <= maxChars) listOf(s) else s.chunkedAtSpaces(maxChars) }
        }
    }

    private fun String.chunkedAtSpaces(maxChars: Int): List<String> {
        val out = mutableListOf<String>()
        var rest = this
        while (rest.length > maxChars) {
            val cut = rest.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
            out += rest.substring(0, cut)
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }
}

/** Small models often wrap their answer in chatter ("Here is the corrected text:"); this removes it. */
object OutputCleaner {
    private val controlTokens = Regex("<\\|im_end\\|>|<\\|im_start\\|>(assistant|user)?|<end_of_turn>|<start_of_turn>(model|user)?|<eos>")
    private val preamble = Regex(
        "^(sure|okay|ok|certainly|of course|here is|here's|here are|below is|corrected|translated|translation|the corrected|the translation|rewritten|shorter version|formal letter|यहाँ|यह रहा|सुधारा गया|अनुवाद)[^\\n]{0,80}:\\s*\\n?",
        RegexOption.IGNORE_CASE,
    )
    // Only labelled notes: a letter may well contain "I hope…" or "Please let me know…".
    private val trailingNote = Regex("\\n+\\s*\\(?(note|explanation|नोट)\\s*:[\\s\\S]*$", RegexOption.IGNORE_CASE)

    /** [original] is the text that was sent; a first line that was already in it is never treated as chatter. */
    fun clean(raw: String, original: String = ""): String? {
        var s = ByteLevelText.repair(raw).replace(controlTokens, "").trim()
        s = s.removePrefix("```").removeSuffix("```").trim()
        if (s.startsWith("text\n", ignoreCase = true)) s = s.substring(5)
        val firstLine = s.lineSequence().firstOrNull().orEmpty().trim()
        if (firstLine.isEmpty() || !original.contains(firstLine)) s = s.replace(preamble, "").trim()
        s = s.replace(trailingNote, "").trim()
        if (s.length >= 2 && s.first() == '"' && s.last() == '"' && s.count { it == '"' } == 2) s = s.substring(1, s.length - 1).trim()
        return s.ifBlank { null }
    }

    /** Small models often leave English in lower case; sentence starts and a lone "i" are fixed here. */
    fun sentenceCase(text: String): String =
        text.replace(Regex("(^|[.!?]\\s+|\\n\\s*)([a-z])")) { it.groupValues[1] + it.groupValues[2].uppercase() }
            .replace(Regex("(?<![A-Za-z'])i(?=['\\s,.!?]|$)"), "I")
}

/**
 * Qwen-style tokenizers store text as bytes mapped to printable characters ("Ġ" is a space).
 * MediaPipe sometimes passes such pieces through undecoded, which turns Hindi into "Ġà¤°…".
 * This maps them back to bytes and decodes the UTF-8. It only acts when a space or newline marker is present,
 * because those never occur in normal writing while accented letters (é) do.
 */
object ByteLevelText {
    private val toByte: Map<Char, Int> by lazy {
        val printable = (('!'.code..'~'.code) + (0xA1..0xAC) + (0xAE..0xFF)).toMutableList()
        val chars = printable.toMutableList()
        var extra = 0
        for (b in 0..255) if (b !in printable) { printable += b; chars += 256 + extra++ }
        printable.indices.associate { chars[it].toChar() to printable[it] }
    }

    fun repair(s: String): String {
        if ('\u0120' !in s && '\u010A' !in s) return s
        val out = java.io.ByteArrayOutputStream()
        for (c in s) {
            val b = toByte[c]
            if (b != null && c.code >= 0x80) out.write(b) else out.write(c.toString().toByteArray(Charsets.UTF_8))
        }
        return out.toString(Charsets.UTF_8.name()).replace("\uFFFD", "")
    }
}
