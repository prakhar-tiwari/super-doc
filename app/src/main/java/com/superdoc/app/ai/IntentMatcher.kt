package com.superdoc.app.ai

/** What a spoken instruction asks for. */
sealed interface VoiceIntent {
    data class Ai(val action: AiAction) : VoiceIntent
    data object ExportPdf : VoiceIntent
    data object ExportWord : VoiceIntent
    data object ReadAloud : VoiceIntent
    data object Send : VoiceIntent
    data object Unknown : VoiceIntent
}

/**
 * Understands short spoken instructions in Hindi or English ("इसकी गलतियाँ ठीक करो", "make it a letter")
 * by keywords. This is instant and works without a model; the model is only asked when nothing matches.
 */
object IntentMatcher {
    private class Rule(val intent: VoiceIntent, val words: List<String>)

    // Checked in this order: the more specific requests come first ("translate and make a PDF" means translate).
    private val rules = listOf(
        Rule(
            VoiceIntent.Ai(AiAction.TRANSLATE),
            listOf("translate", "translation", "in english", "in hindi", "into english", "into hindi",
                "अनुवाद", "अंग्रेजी में", "इंग्लिश में", "english में", "हिंदी में", "हिन्दी में", "ट्रांसलेट",
                "anuvad", "anuvaad", "angrezi mein", "angreji mein", "hindi mein", "english mein"),
        ),
        Rule(
            VoiceIntent.Ai(AiAction.MAKE_LETTER),
            listOf("letter", "application", "पत्र", "चिट्ठी", "चिठ्ठी", "आवेदन", "एप्लीकेशन", "अर्जी", "लेटर",
                "patra", "chitthi", "chithi", "avedan", "arzi", "arji"),
        ),
        Rule(
            VoiceIntent.Ai(AiAction.MAKE_SHORTER),
            listOf("shorter", "short", "summary", "summarise", "summarize", "छोटा", "छोटी", "कम कर", "संक्षेप", "सारांश",
                "chhota", "chota", "chhoti", "choti", "kam kar", "sankshep"),
        ),
        Rule(
            VoiceIntent.Ai(AiAction.FIX_SPELLING),
            listOf("spelling", "grammar", "mistake", "mistakes", "correct", "fix", "errors",
                "गलती", "गलतियां", "गलतियाँ", "सुधार", "ठीक कर", "ठीक करो", "स्पेलिंग", "व्याकरण", "शुद्ध",
                "galti", "galtiyan", "galtiya", "sudhar", "sudharo", "theek kar", "thik kar", "theek karo", "thik karo", "shuddh"),
        ),
        Rule(
            VoiceIntent.Ai(AiAction.MAKE_NEAT),
            listOf("neat", "format", "tidy", "clean", "nice", "arrange",
                "सुंदर", "साफ", "व्यवस्थित", "ढंग से", "अच्छा बना", "अच्छे से", "सजा",
                "saaf", "saf", "sundar", "dhang se", "achha bana", "acche se", "achhe se", "saja"),
        ),
        Rule(VoiceIntent.ExportWord, listOf("word file", "word", "docx", "वर्ड")),
        Rule(VoiceIntent.ExportPdf, listOf("pdf", "पीडीएफ", "पी डी एफ", "document", "दस्तावेज", "dastavej", "dastavez")),
        Rule(VoiceIntent.ReadAloud, listOf("read", "speak", "पढ़", "पढ़कर", "पढ़ो", "सुना", "बोलकर",
            "padh", "padho", "padhkar", "suna", "sunao", "bolkar")),
        Rule(VoiceIntent.Send, listOf("send", "share", "whatsapp", "भेज", "भेजो", "व्हाट्सएप", "व्हाट्सऐप", "शेयर",
            "bhej", "bhejo", "bhej do")),
    )

    fun match(spoken: String): VoiceIntent {
        val text = normalize(spoken)
        if (text.isBlank()) return VoiceIntent.Unknown
        return rules.firstOrNull { rule -> rule.words.any { contains(text, normalize(it)) } }?.intent ?: VoiceIntent.Unknown
    }

    /** Reads the one-word answer of [AiPrompts.classify]. */
    fun fromModelAnswer(answer: String): VoiceIntent {
        val word = Regex("[A-Za-z]+").find(answer.uppercase())?.value ?: return VoiceIntent.Unknown
        return when (word) {
            "FIX" -> VoiceIntent.Ai(AiAction.FIX_SPELLING)
            "NEAT" -> VoiceIntent.Ai(AiAction.MAKE_NEAT)
            "LETTER" -> VoiceIntent.Ai(AiAction.MAKE_LETTER)
            "SHORTER" -> VoiceIntent.Ai(AiAction.MAKE_SHORTER)
            "TRANSLATE" -> VoiceIntent.Ai(AiAction.TRANSLATE)
            "PDF" -> VoiceIntent.ExportPdf
            "WORD" -> VoiceIntent.ExportWord
            "READ" -> VoiceIntent.ReadAloud
            "SEND" -> VoiceIntent.Send
            else -> VoiceIntent.Unknown
        }
    }

    /**
     * Lower case, and nukta dots removed so "ज़रूरी" and "जरूरी" match; speech recognisers vary.
     * Nukta letters can be one code point (U+0958–U+095F) or a letter plus U+093C, so both are handled.
     */
    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase()) {
            when (c) {
                '\u093C' -> Unit
                in '\u0958'..'\u095F' -> sb.append(NUKTA_BASE[c - '\u0958'])
                else -> sb.append(c)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private const val NUKTA_BASE = "कखगजडढफय"

    /** English keywords must be whole words ("word" must not match "words" or "sword"); Hindi ones may be word stems. */
    private fun contains(text: String, word: String): Boolean {
        val latin = word.all { it.code < 128 }
        return if (latin) Regex("(^|[^a-z])${Regex.escape(word)}($|[^a-z])").containsMatchIn(text) else text.contains(word)
    }
}
