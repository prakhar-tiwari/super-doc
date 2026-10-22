package com.superdoc.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ItemType { NOTE, PDF, DOCX }

/**
 * One entry in "My Documents". Notes keep their text in [body]; PDF and Word
 * documents point at a file inside app-private storage via [fileName].
 */
@Entity(tableName = "items", indices = [Index("deletedAt"), Index("updatedAt")])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val title: String,
    val titleIsAuto: Boolean = true,
    val body: String = "",
    val fileName: String? = null,
    val pageCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val isSample: Boolean = false,
) {
    val isInRecycleBin: Boolean get() = deletedAt != null
}
