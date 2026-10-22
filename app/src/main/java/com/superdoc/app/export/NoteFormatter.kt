package com.superdoc.app.export

/** How a note should be laid out when it becomes a document. */
enum class Template { PLAIN, LETTER, LIST }

enum class Align { START, END }

sealed interface Block {
    data class Title(val text: String) : Block
    data class Paragraph(val text: String, val align: Align = Align.START, val bold: Boolean = false) : Block
    data class Bullet(val text: String) : Block
    data class Numbered(val number: Int, val text: String) : Block
    data object Spacer : Block
}

/** A layout-independent document; [NotePdfWriter] and [DocxWriter] both draw from this. */
data class FormattedDoc(val blocks: List<Block>, val hindi: Boolean)

data class LetterFields(val to: String = "", val subject: String = "", val signer: String = "")

/** The fixed words of a letter, in the language the note is written in (not the app language). */
data class LetterWords(
    val date: String,
    val to: String,
    val subject: String,
    val salutation: String,
    val closing: String,
) {
    companion object {
        val ENGLISH = LetterWords("Date:", "To,", "Subject:", "Respected Sir/Madam,", "Yours faithfully,")
        val HINDI = LetterWords("दिनांक:", "सेवा में,", "विषय:", "महोदय/महोदया,", "भवदीय,")
    }
}

/**
 * Rule-based formatting that needs no AI:
 * blank lines separate paragraphs, lines starting with "-", "*" or "•" become bullets,
 * and "1." / "1)" become numbered items.
 */
object NoteFormatter {
    private val bulletRegex = Regex("^\\s*[-*•●▪]\\s+(.*)$")
    private val numberRegex = Regex("^\\s*(\\d{1,3})[.)]\\s+(.*)$")
    private val listTitleWords = listOf("list", "सूची", "लिस्ट", "items", "सामान")

    fun format(text: String, template: Template, hindi: Boolean, date: String, fields: LetterFields = LetterFields()): FormattedDoc {
        val blocks = when (template) {
            Template.PLAIN -> body(text)
            Template.LIST -> list(text)
            Template.LETTER -> letter(text, if (hindi) LetterWords.HINDI else LetterWords.ENGLISH, date, fields)
        }
        return FormattedDoc(blocks, hindi)
    }

    /** Paragraphs and lists, keeping the user's own line breaks inside a paragraph. */
    fun body(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += Block.Paragraph(paragraph.joinToString("\n"))
                paragraph.clear()
            }
        }
        for (raw in text.replace("\r\n", "\n").lines()) {
            val line = raw.trimEnd()
            val bullet = bulletRegex.find(line)
            val numbered = numberRegex.find(line)
            when {
                line.isBlank() -> flush()
                bullet != null -> { flush(); blocks += Block.Bullet(bullet.groupValues[1].trim()) }
                numbered != null -> { flush(); blocks += Block.Numbered(numbered.groupValues[1].toInt(), numbered.groupValues[2].trim()) }
                else -> paragraph += line.trim()
            }
        }
        flush()
        return blocks
    }

    fun list(text: String): List<Block> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val first = lines.first()
        val hasTitle = lines.size > 1 && (first.endsWith(":") || listTitleWords.any { first.contains(it, ignoreCase = true) })
        val blocks = mutableListOf<Block>()
        if (hasTitle) blocks += Block.Title(first.trimEnd(':').trim())
        lines.drop(if (hasTitle) 1 else 0).forEach { line ->
            val clean = bulletRegex.find(line)?.groupValues?.get(1)
                ?: numberRegex.find(line)?.groupValues?.get(2)
                ?: line
            blocks += Block.Bullet(clean.trim())
        }
        return blocks
    }

    fun letter(text: String, words: LetterWords, date: String, fields: LetterFields): List<Block> {
        val blocks = mutableListOf<Block>()
        blocks += Block.Paragraph("${words.date} $date", Align.END)
        blocks += Block.Spacer
        if (fields.to.isNotBlank()) {
            blocks += Block.Paragraph(words.to)
            blocks += Block.Paragraph(fields.to.trim())
            blocks += Block.Spacer
        }
        if (fields.subject.isNotBlank()) {
            blocks += Block.Paragraph("${words.subject} ${fields.subject.trim()}", bold = true)
            blocks += Block.Spacer
        }
        blocks += Block.Paragraph(words.salutation)
        blocks += body(text)
        blocks += Block.Spacer
        blocks += Block.Paragraph(words.closing)
        if (fields.signer.isNotBlank()) blocks += Block.Paragraph(fields.signer.trim())
        return blocks
    }
}
