package com.superdoc.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** On-device OCR. The Devanagari model reads both Hindi and English. */
@Singleton
class TextReader @Inject constructor(@ApplicationContext private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    suspend fun read(uri: Uri): Text? = runCatching {
        recognizer.process(InputImage.fromFilePath(context, uri)).await()
    }.getOrNull()

    suspend fun read(bitmap: Bitmap): Text? = runCatching {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
    }.getOrNull()

    companion object {
        /** Picks the heading-like line: the tallest text near the top of the page. */
        fun headingOf(text: Text?, imageHeight: Int): String? {
            if (text == null) return null
            val lines = text.textBlocks.flatMap { it.lines }
                .filter { line -> line.text.count { it.isLetter() } >= 3 && line.text.length <= 60 }
            if (lines.isEmpty()) return null
            val limit = if (imageHeight > 0) imageHeight * 0.4f else Float.MAX_VALUE
            val top = lines.filter { (it.boundingBox?.top ?: 0) < limit }.ifEmpty { lines }
            return top.maxByOrNull { it.boundingBox?.height() ?: 0 }?.text?.trim()
        }
    }
}
