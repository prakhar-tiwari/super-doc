package com.superdoc.app.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemType
import com.superdoc.app.pdf.PdfEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Turns a scan into a document in "My Documents", named after the heading found on the first page. */
@Singleton
class ScanSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: FileStore,
    private val pdf: PdfEngine,
    private val reader: TextReader,
    private val documents: DocumentsRepository,
) {
    suspend fun save(result: ScanResult): Long = withContext(Dispatchers.IO) {
        val file: File = if (result.pdf != null) {
            files.copyIntoDocs(result.pdf, "pdf")
        } else {
            files.newDocFile("pdf").also { pdf.imagesToPdf(result.pages, it) }
        }
        val pages = pdf.pageCount(file).takeIf { it > 0 } ?: result.pages.size
        val heading = result.pages.firstOrNull()?.let { headingOf(it) }
        documents.addFile(file, ItemType.PDF, R.string.name_prefix_scan, heading, pages)
    }

    /** Turns photos into a temporary PDF, used when adding photo pages to an existing document. */
    suspend fun photosToTempPdf(uris: List<Uri>): File = withContext(Dispatchers.IO) {
        files.newWorkFile("pdf").also { pdf.imagesToPdf(uris, it) }
    }

    /** A temporary copy of the scanner's PDF (or a PDF built from its photos). */
    suspend fun scanToTempPdf(result: ScanResult): File = withContext(Dispatchers.IO) {
        val out = files.newWorkFile("pdf")
        if (result.pdf != null) {
            context.contentResolver.openInputStream(result.pdf)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        } else {
            pdf.imagesToPdf(result.pages, out)
        }
        out
    }

    private suspend fun headingOf(image: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(image)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        return TextReader.headingOf(reader.read(image), bounds.outHeight)
    }
}
