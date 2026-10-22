package com.superdoc.app.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** All documents live in app-private storage; the user never browses folders. */
@Singleton
class FileStore @Inject constructor(@ApplicationContext private val context: Context) {

    val docsDir: File get() = File(context.filesDir, "docs").apply { mkdirs() }
    val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }
    val shareDir: File get() = File(context.cacheDir, "share").apply { mkdirs() }
    val workDir: File get() = File(context.cacheDir, "work").apply { mkdirs() }

    fun newDocFile(extension: String): File = File(docsDir, "doc_${UUID.randomUUID()}.$extension")

    fun newWorkFile(extension: String): File = File(workDir, "tmp_${UUID.randomUUID()}.$extension")

    fun docFile(fileName: String): File = File(docsDir, fileName)

    fun copyIntoDocs(uri: Uri, extension: String): File = copy(uri, newDocFile(extension))

    fun copyIntoWork(uri: Uri, extension: String): File = copy(uri, newWorkFile(extension))

    private fun copy(uri: Uri, target: File): File {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open $uri" }
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    /** Copies a document to the share folder under a friendly name, so the receiver sees a meaningful file name. */
    fun shareCopy(source: File, title: String, extension: String): File {
        shareDir.listFiles()?.forEach { it.delete() }
        val target = File(shareDir, "${safeFileName(title)}.$extension")
        source.copyTo(target, overwrite = true)
        return target
    }

    companion object {
        fun safeFileName(title: String): String {
            val cleaned = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('.', '…')
                .take(80)
                .trim()
            return cleaned.ifEmpty { "SuperDoc" }
        }
    }
}
