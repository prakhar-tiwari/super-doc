package com.superdoc.app.backup

import android.content.Context
import android.net.Uri
import com.superdoc.app.data.FamilyHelper
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemDao
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RestoreResult(val added: Int, val alreadyThere: Int)

/** Saves all documents to one zip the user can keep on Google Drive or a pen drive, and brings them back. */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ItemDao,
    private val files: FileStore,
    private val settings: SettingsRepository,
) {
    fun suggestedName(now: Long = System.currentTimeMillis()): String =
        "Super Doc backup ${SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(Date(now))}.zip"

    /** Returns how many documents were saved. */
    suspend fun export(target: Uri): Int = withContext(Dispatchers.IO) {
        val items = dao.all().filter { it.deletedAt == null && !it.isSample }
        val s = settings.current()
        val manifest = BackupManifest(
            createdAt = System.currentTimeMillis(),
            settings = BackupSettings(s.signatureName, s.family?.name.orEmpty(), s.family?.phone.orEmpty()),
            items = items.map {
                BackupItem(it.type.name, it.title, it.titleIsAuto, it.body, it.fileName, it.pageCount, it.createdAt, it.updatedAt)
            },
        )
        val out = requireNotNull(context.contentResolver.openOutputStream(target, "wt")) { "Cannot write backup" }
        out.use { BackupArchive.write(it, manifest) { name -> files.docFile(name) } }
        items.size
    }

    suspend fun restore(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val staging = File(files.workDir, "restore_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val room = (files.docsDir.usableSpace - FREE_SPACE_RESERVE).coerceAtLeast(0)
            val input = requireNotNull(context.contentResolver.openInputStream(source)) { "Cannot open backup" }
            val manifest = input.use { BackupArchive.read(it, staging, room) }

            val existing = dao.all().filter { it.deletedAt == null }
                .map { Triple(it.type.name, it.title, it.createdAt) }.toSet()
            var added = 0
            var skipped = 0
            manifest.items.forEach { item ->
                if (Triple(item.type, item.title, item.createdAt) in existing) {
                    skipped++
                    return@forEach
                }
                val fileName = item.file?.let { name ->
                    val staged = File(staging, name)
                    val target = files.newDocFile(if (item.type == ItemType.DOCX.name) "docx" else "pdf")
                    if (!staged.renameTo(target)) staged.copyTo(target, overwrite = true)
                    target.name
                }
                dao.insert(
                    ItemEntity(
                        type = ItemType.valueOf(item.type),
                        title = item.title.ifBlank { "Super Doc" },
                        titleIsAuto = item.titleIsAuto,
                        body = item.body,
                        fileName = fileName,
                        pageCount = item.pageCount,
                        createdAt = item.createdAt,
                        updatedAt = item.updatedAt,
                    ),
                )
                added++
            }

            val current = settings.current()
            if (current.signatureName.isBlank() && manifest.settings.signatureName.isNotBlank()) {
                settings.setSignatureName(manifest.settings.signatureName)
            }
            if (current.family == null && manifest.settings.familyPhone.isNotBlank()) {
                settings.setFamily(FamilyHelper(manifest.settings.familyName, manifest.settings.familyPhone))
            }
            RestoreResult(added, skipped)
        } finally {
            staging.deleteRecursively()
        }
    }

    private companion object {
        const val FREE_SPACE_RESERVE = 200L * 1024 * 1024
    }
}
