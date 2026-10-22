package com.superdoc.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds meaningful names like "Note - Doctor appointment on Monday - 22 Oct 2026". */
object AutoNamer {
    private const val MAX_WORDS = 5
    private const val MAX_SNIPPET = 32

    fun formatDate(time: Long, locale: Locale): String =
        SimpleDateFormat("d MMM yyyy", locale).format(Date(time))

    fun title(prefix: String, text: String?, time: Long, locale: Locale): String {
        val snippet = snippet(text)
        val date = formatDate(time, locale)
        return if (snippet.isEmpty()) "$prefix - $date" else "$prefix - $snippet - $date"
    }

    /** First meaningful line of text, shortened to a few words. */
    fun snippet(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val line = text.lineSequence()
            .map { it.trim().trimStart('-', '*', '•', '#', ' ') }
            .firstOrNull { line -> line.count { it.isLetter() } >= 3 }
            ?: return ""
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        var result = words.take(MAX_WORDS).joinToString(" ")
        val truncated = words.size > MAX_WORDS || result.length > MAX_SNIPPET
        if (result.length > MAX_SNIPPET) result = result.take(MAX_SNIPPET).trimEnd()
        result = result.trimEnd(',', '.', ':', ';', '-', '।')
        return if (truncated) "$result…" else result
    }

    /**
     * The meaningful middle of a name, used to name documents made from it:
     * "Scan - Gas bill - 22 Oct 2026 (2)" gives "Gas bill". Names the user typed are kept whole.
     */
    fun core(title: String, isAuto: Boolean): String {
        val clean = title.replace(Regex("\\s\\(\\d+\\)$"), "").trim()
        if (!isAuto) return clean
        val parts = clean.split(" - ")
        return if (parts.size >= 3) parts.subList(1, parts.size - 1).joinToString(" - ") else ""
    }

    /** Adds " (2)", " (3)" ... when the same name already exists. */
    fun unique(base: String, existing: Collection<String>): String {
        if (base !in existing) return base
        var n = 2
        while ("$base ($n)" in existing) n++
        return "$base ($n)"
    }
}
