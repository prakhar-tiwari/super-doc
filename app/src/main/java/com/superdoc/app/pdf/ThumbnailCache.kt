package com.superdoc.app.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders PDF pages to bitmaps with Android's PdfRenderer and keeps recent ones in memory.
 * The cache is small and cleared on low memory so budget phones stay responsive.
 */
@Singleton
class ThumbnailCache @Inject constructor() {
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    // PdfRenderer allows one open page at a time; serialise all rendering.
    private val mutex = Mutex()

    suspend fun render(file: File, pageIndex: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${file.absolutePath}:${file.lastModified()}:$pageIndex:$widthPx"
        cache.get(key)?.let { return@withContext it }
        mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@use null
                        renderer.openPage(pageIndex).use { page ->
                            val width = widthPx.coerceAtLeast(64)
                            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(64)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    fun clear() = cache.evictAll()
}
