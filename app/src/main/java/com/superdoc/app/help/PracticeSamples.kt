package com.superdoc.app.help

import android.content.Context
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.data.NotesRepository
import com.superdoc.app.export.ExportFormat
import com.superdoc.app.export.LetterFields
import com.superdoc.app.export.NoteExporter
import com.superdoc.app.export.Template
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Practice examples: a note (with a few spelling mistakes, to try the AI helper on) and a one-page
 * letter PDF. They carry a "Practice" badge, so it is safe to open, send, change or delete them.
 */
@Singleton
class PracticeSamples @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notes: NotesRepository,
    private val exporter: NoteExporter,
    private val documents: DocumentsRepository,
) {
    suspend fun has(): Boolean = documents.hasSamples()

    /** Adds the examples in the app's current language; existing ones are replaced. */
    suspend fun add() {
        documents.removeSamples()
        val letterBody = AppLocale.string(context, R.string.practice_letter_body)
        val now = System.currentTimeMillis()
        val letter = ItemEntity(type = ItemType.NOTE, title = "", body = letterBody, createdAt = now, updatedAt = now)
        val fields = LetterFields(
            to = AppLocale.string(context, R.string.practice_letter_to),
            subject = AppLocale.string(context, R.string.practice_letter_subject),
            signer = AppLocale.string(context, R.string.practice_letter_signer),
        )
        exporter.export(letter, Template.LETTER, fields, ExportFormat.PDF, previewPages = 1, isSample = true)
        notes.create(AppLocale.string(context, R.string.practice_note_body), isSample = true)
    }

    suspend fun remove() = documents.removeSamples()
}
