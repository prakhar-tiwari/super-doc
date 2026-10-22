package com.superdoc.app.share

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.superdoc.app.data.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves a copy into Download/SuperDoc so other apps (and file managers) can find it. */
object SaveToPhone {
    const val FOLDER = "SuperDoc"

    /** True when Android 8-9 still needs the storage permission for this. */
    val needsLegacyPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun save(context: Context, source: File, title: String, mime: String): String? = withContext(Dispatchers.IO) {
        val name = "${FileStore.safeFileName(title)}.${source.extension}"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER).apply { mkdirs() }
                source.copyTo(File(dir, name), overwrite = true)
            }
            "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$name"
        }.getOrNull()
    }
}
