package com.superdoc.app.data

import android.content.Context
import com.superdoc.app.R
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ItemDao,
    private val files: FileStore,
) {
    fun observeLibrary(query: String): Flow<List<ItemEntity>> =
        if (query.isBlank()) dao.observeActive() else dao.search(query.trim())

    fun observePdfs(): Flow<List<ItemEntity>> = dao.observePdfs()

    fun observeRecycleBin(): Flow<List<ItemEntity>> = dao.observeRecycleBin()

    fun observe(id: Long): Flow<ItemEntity?> = dao.observe(id)

    suspend fun get(id: Long): ItemEntity? = dao.get(id)

    fun fileOf(item: ItemEntity): File? = item.fileName?.let { files.docFile(it) }

    /**
     * Registers a finished file in "My Documents".
     * [nameHint] is text the name is derived from (OCR text, note text or an explicit title).
     */
    suspend fun addFile(
        file: File,
        type: ItemType,
        prefixRes: Int,
        nameHint: String?,
        pageCount: Int,
        isSample: Boolean = false,
    ): Long {
        val now = System.currentTimeMillis()
        val prefix = AppLocale.string(context, prefixRes)
        val base = AutoNamer.title(prefix, nameHint, now, AppLocale.current())
        val title = AutoNamer.unique(base, dao.activeTitles(0))
        return dao.insert(
            ItemEntity(
                type = type, title = title, fileName = file.name, pageCount = pageCount,
                createdAt = now, updatedAt = now, isSample = isSample,
            )
        )
    }

    /** Replaces the file behind an existing document, e.g. after adding pages. */
    suspend fun replaceFile(id: Long, newFile: File, pageCount: Int) {
        val item = dao.get(id) ?: return
        val old = fileOf(item)
        dao.update(item.copy(fileName = newFile.name, pageCount = pageCount, updatedAt = System.currentTimeMillis()))
        if (old != null && old != newFile) old.delete()
    }

    suspend fun rename(id: Long, newTitle: String) {
        val item = dao.get(id) ?: return
        val clean = newTitle.trim()
        if (clean.isEmpty()) return
        dao.update(
            item.copy(
                title = AutoNamer.unique(clean, dao.activeTitles(id)),
                titleIsAuto = false,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun moveToRecycleBin(id: Long) {
        val item = dao.get(id) ?: return
        dao.update(item.copy(deletedAt = System.currentTimeMillis()))
    }

    suspend fun restore(id: Long) {
        val item = dao.get(id) ?: return
        dao.update(
            item.copy(
                deletedAt = null,
                title = AutoNamer.unique(item.title, dao.activeTitles(id)),
            )
        )
    }

    suspend fun deleteForever(id: Long) {
        val item = dao.get(id) ?: return
        fileOf(item)?.delete()
        dao.deleteForever(id)
    }

    suspend fun emptyRecycleBin() {
        dao.deletedBefore(Long.MAX_VALUE).forEach { deleteForever(it.id) }
    }

    /** Items stay in the Recycle Bin for 30 days. */
    suspend fun purgeOldRecycleBin() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RECYCLE_DAYS)
        dao.deletedBefore(cutoff).forEach { deleteForever(it.id) }
    }

    suspend fun removeSamples() {
        dao.samples().forEach { deleteForever(it.id) }
    }

    suspend fun hasSamples(): Boolean = dao.samples().isNotEmpty()

    companion object {
        const val RECYCLE_DAYS = 30L

        fun prefixFor(type: ItemType): Int = when (type) {
            ItemType.NOTE -> R.string.name_prefix_note
            ItemType.PDF -> R.string.name_prefix_document
            ItemType.DOCX -> R.string.name_prefix_word
        }
    }
}
