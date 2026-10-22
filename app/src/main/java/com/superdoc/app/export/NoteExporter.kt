package com.superdoc.app.export

import com.superdoc.app.data.AutoNamer
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.util.Script
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat(val extension: String, val type: ItemType) {
    PDF("pdf", ItemType.PDF),
    WORD("docx", ItemType.DOCX),
}

@Singleton
class NoteExporter @Inject constructor(
    private val pdfWriter: NotePdfWriter,
    private val files: FileStore,
    private val documents: DocumentsRepository,
) {
    fun format(note: ItemEntity, template: Template, fields: LetterFields): FormattedDoc {
        val hindi = Script.isMostlyHindi(note.body + fields.to + fields.subject)
        val date = AutoNamer.formatDate(System.currentTimeMillis(), Script.localeFor(note.body))
        return NoteFormatter.format(note.body, template, hindi, date, fields)
    }

    /** A temporary PDF used for the on-screen preview (Word files share the same layout). */
    suspend fun preview(note: ItemEntity, template: Template, fields: LetterFields): Pair<File, Int> {
        val out = files.newWorkFile("pdf")
        val pages = pdfWriter.write(format(note, template, fields), out)
        return out to pages
    }

    suspend fun export(
        note: ItemEntity,
        template: Template,
        fields: LetterFields,
        format: ExportFormat,
        previewPages: Int,
        isSample: Boolean = false,
    ): Long = withContext(Dispatchers.IO) {
        val doc = format(note, template, fields)
        val out = files.newDocFile(format.extension)
        val pages = when (format) {
            ExportFormat.PDF -> pdfWriter.write(doc, out)
            ExportFormat.WORD -> {
                out.outputStream().use { DocxWriter.write(doc, it) }
                previewPages
            }
        }
        val nameHint = fields.subject.ifBlank { note.body }
        documents.addFile(out, format.type, DocumentsRepository.prefixFor(format.type), nameHint, pages, isSample)
    }
}
