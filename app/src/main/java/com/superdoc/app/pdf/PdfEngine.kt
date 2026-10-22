package com.superdoc.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.superdoc.app.data.FileStore
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/** The document is protected with a password, so pages can't be changed. */
class PdfLockedException(cause: Throwable) : IOException(cause)

/**
 * Page operations with PdfBox-Android. None of these touch text, so Hindi shaping is not
 * involved (notes are turned into PDFs by [NotePdfWriter] instead).
 * Everything writes to a new file; the original is never modified.
 */
@Singleton
class PdfEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: FileStore,
) {
    private fun memory() = MemoryUsageSetting.setupTempFileOnly().setTempDir(files.workDir)

    private fun <T> open(file: File, block: (PDDocument) -> T): T {
        val doc = try {
            PDDocument.load(file, memory())
        } catch (e: InvalidPasswordException) {
            throw PdfLockedException(e)
        }
        return doc.use(block)
    }

    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        runCatching { open(file) { it.numberOfPages } }.getOrElse {
            if (it is PdfLockedException) throw it else 0
        }
    }

    /** Builds a PDF from photos: one A4 page per photo, JPEG-compressed to keep the file small for WhatsApp. */
    suspend fun imagesToPdf(uris: List<Uri>, out: File): Int = withContext(Dispatchers.IO) {
        PDDocument(memory()).use { doc ->
            uris.forEach { uri -> addImagePage(doc, uri) }
            doc.save(out)
            doc.numberOfPages
        }
    }

    private fun addImagePage(doc: PDDocument, uri: Uri) {
        val bitmap = decodeScaled(uri, MAX_IMAGE_SIDE) ?: return
        try {
            val landscape = bitmap.width > bitmap.height
            val box = if (landscape) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
            val page = PDPage(box)
            doc.addPage(page)
            val image = JPEGFactory.createFromImage(doc, bitmap, JPEG_QUALITY)
            val scale = min(box.width / bitmap.width, box.height / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(image, (box.width - w) / 2f, (box.height - h) / 2f, w, h)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the photo's rotation (EXIF) automatically.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val s = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    /** Joins documents in the given order. */
    suspend fun merge(inputs: List<File>, out: File): Int = withContext(Dispatchers.IO) {
        inputs.forEach { f -> open(f) { } } // fail early, with a clear error, on locked files
        PDFMergerUtility().apply {
            destinationFileName = out.absolutePath
            inputs.forEach { addSource(it) }
            mergeDocuments(memory())
        }
        pageCount(out)
    }

    /**
     * Writes a new PDF containing [pages] (0-based) of [input], in that order.
     * Used for removing pages (keep the rest), reordering, and splitting.
     */
    suspend fun select(input: File, pages: List<Int>, out: File): Int = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "No pages selected" }
        open(input) { src ->
            PDDocument(memory()).use { dst ->
                pages.forEach { i -> dst.importPage(src.getPage(i)) }
                dst.save(out)
                dst.numberOfPages
            }
        }
    }

    /** Puts all pages of [insert] into [base] so they start at page position [at] (0-based). */
    suspend fun insert(base: File, insert: File, at: Int, out: File): Int = withContext(Dispatchers.IO) {
        open(base) { src ->
            open(insert) { extra ->
                PDDocument(memory()).use { dst ->
                    val position = at.coerceIn(0, src.numberOfPages)
                    for (i in 0 until position) dst.importPage(src.getPage(i))
                    for (i in 0 until extra.numberOfPages) dst.importPage(extra.getPage(i))
                    for (i in position until src.numberOfPages) dst.importPage(src.getPage(i))
                    dst.save(out)
                    dst.numberOfPages
                }
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 2000
        const val JPEG_QUALITY = 0.8f
    }
}
