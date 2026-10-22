package com.superdoc.app.pdf

import android.content.Context
import com.superdoc.app.R
import com.superdoc.app.data.AutoNamer
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "More tools" actions. Each one writes new documents into "My Documents";
 * the document the user started from is never changed.
 */
@Singleton
class DocumentTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: PdfEngine,
    private val files: FileStore,
    private val documents: DocumentsRepository,
) {
    suspend fun removePages(item: ItemEntity, pageCount: Int, remove: Set<Int>): Long {
        val keep = (0 until pageCount).filterNot { it in remove }
        return edited(item) { src, out -> engine.select(src, keep, out) }
    }

    suspend fun reorder(item: ItemEntity, order: List<Int>): Long =
        edited(item) { src, out -> engine.select(src, order, out) }

    /** Puts the pages of [extra] (a temporary file, deleted afterwards) at position [at]. */
    suspend fun insert(item: ItemEntity, extra: File, at: Int): Long = try {
        edited(item) { src, out -> engine.insert(src, extra, at, out) }
    } finally {
        if (extra.parentFile == files.workDir) extra.delete()
    }

    suspend fun merge(items: List<ItemEntity>): Long {
        val inputs = items.map { source(it) }
        val out = files.newDocFile("pdf")
        val pages = produce(out) { engine.merge(inputs, out) }
        val hint = items.firstOrNull()?.let { AutoNamer.core(it.title, it.titleIsAuto) }
        return documents.addFile(out, ItemType.PDF, R.string.name_prefix_merged, hint, pages)
    }

    /** One new document per entry of [parts]; each entry lists 0-based page numbers. */
    suspend fun split(item: ItemEntity, parts: List<List<Int>>): List<Long> {
        val src = source(item)
        val hint = AutoNamer.core(item.title, item.titleIsAuto)
        return parts.mapIndexed { i, pages ->
            val out = files.newDocFile("pdf")
            val count = produce(out) { engine.select(src, pages, out) }
            val prefix = AppLocale.string(context, R.string.name_prefix_part_n, i + 1)
            documents.addFile(out, ItemType.PDF, prefix, hint, count)
        }
    }

    private suspend fun edited(item: ItemEntity, block: suspend (File, File) -> Int): Long {
        val src = source(item)
        val out = files.newDocFile("pdf")
        val pages = produce(out) { block(src, out) }
        return documents.addFile(out, ItemType.PDF, R.string.name_prefix_changed, AutoNamer.core(item.title, item.titleIsAuto), pages)
    }

    private fun source(item: ItemEntity): File =
        documents.fileOf(item)?.takeIf { it.exists() } ?: throw FileNotFoundException(item.title)

    /** Runs [block] and removes a half-written [out] file if it fails. */
    private suspend fun produce(out: File, block: suspend () -> Int): Int = try {
        block()
    } catch (t: Throwable) {
        out.delete()
        throw t
    }

    companion object {
        /** "Cut into two" after page [after] (1-based): pages 1..after and after+1..count. */
        fun cutInTwo(pageCount: Int, after: Int): List<List<Int>> =
            listOf((0 until after).toList(), (after until pageCount).toList())

        fun everyPage(pageCount: Int): List<List<Int>> = (0 until pageCount).map { listOf(it) }
    }
}
