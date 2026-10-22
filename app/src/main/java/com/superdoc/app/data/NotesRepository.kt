package com.superdoc.app.data

import android.content.Context
import com.superdoc.app.R
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ItemDao,
) {
    fun observe(id: Long): Flow<ItemEntity?> = dao.observe(id)

    suspend fun get(id: Long): ItemEntity? = dao.get(id)

    suspend fun create(body: String = "", isSample: Boolean = false): Long {
        val now = System.currentTimeMillis()
        val title = autoTitle(body, now, 0)
        return dao.insert(
            ItemEntity(
                type = ItemType.NOTE, title = title, body = body,
                createdAt = now, updatedAt = now, isSample = isSample,
            )
        )
    }

    suspend fun updateBody(id: Long, body: String) {
        val item = dao.get(id) ?: return
        if (item.body == body) return
        val title = if (item.titleIsAuto) autoTitle(body, item.createdAt, id) else item.title
        dao.update(item.copy(body = body, title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun rename(id: Long, newTitle: String) {
        val item = dao.get(id) ?: return
        val clean = newTitle.trim()
        if (clean.isEmpty()) {
            dao.update(item.copy(title = autoTitle(item.body, item.createdAt, id), titleIsAuto = true))
        } else {
            dao.update(item.copy(title = AutoNamer.unique(clean, dao.activeTitles(id)), titleIsAuto = false))
        }
    }

    /** Notes the user opened but never wrote in are removed, so the list doesn't fill with empty notes. */
    suspend fun discardIfEmpty(id: Long) {
        val item = dao.get(id) ?: return
        if (item.type == ItemType.NOTE && item.body.isBlank() && item.titleIsAuto) dao.deleteForever(id)
    }

    private suspend fun autoTitle(body: String, createdAt: Long, id: Long): String {
        val prefix = AppLocale.string(context, R.string.name_prefix_note)
        val base = AutoNamer.title(prefix, body, createdAt, AppLocale.current())
        return AutoNamer.unique(base, dao.activeTitles(id))
    }
}
