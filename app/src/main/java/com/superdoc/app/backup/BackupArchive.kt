package com.superdoc.app.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One document as written into a backup. [file] is the stored file name, or null for notes. */
data class BackupItem(
    val type: String,
    val title: String,
    val titleIsAuto: Boolean,
    val body: String,
    val file: String?,
    val pageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupSettings(
    val signatureName: String = "",
    val familyName: String = "",
    val familyPhone: String = "",
)

data class BackupManifest(val createdAt: Long, val items: List<BackupItem>, val settings: BackupSettings)

class BackupFormatException(message: String) : IOException(message)

/**
 * A backup is one zip file: `superdoc-backup.json` first, then `files/<name>` for each PDF or Word file.
 * Reading is defensive because the zip may come from anywhere: only those two kinds of entry are
 * accepted, names may not contain folders or "..", and sizes are capped.
 */
object BackupArchive {
    const val MANIFEST = "superdoc-backup.json"
    private const val FILES = "files/"
    private const val FORMAT = "superdoc-backup"
    private const val VERSION = 1
    private const val MAX_MANIFEST_BYTES = 32L * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,120}")

    fun isSafeFileName(name: String): Boolean = SAFE_NAME.matches(name) && !name.contains("..")

    fun write(out: OutputStream, manifest: BackupManifest, fileFor: (String) -> File?) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(toJson(manifest).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            manifest.items.mapNotNull { it.file }.distinct().forEach { name ->
                val source = fileFor(name)?.takeIf { it.isFile } ?: return@forEach
                zip.putNextEntry(ZipEntry(FILES + name))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Unpacks into [staging] (which must be empty) and returns the manifest. Items whose file is
     * missing from the zip are dropped. Stops if more than [maxBytes] would be written.
     */
    fun read(input: InputStream, staging: File, maxBytes: Long): BackupManifest {
        val root = staging.canonicalFile
        var manifest: BackupManifest? = null
        val unpacked = mutableSetOf<String>()
        var written = 0L
        var entries = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw BackupFormatException("Too many entries")
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == MANIFEST -> {
                        val bytes = readCapped(zip, MAX_MANIFEST_BYTES)
                        manifest = parse(String(bytes, Charsets.UTF_8))
                    }
                    name.startsWith(FILES) && isSafeFileName(name.removePrefix(FILES)) -> {
                        val target = File(root, name.removePrefix(FILES)).canonicalFile
                        if (target.parentFile != root) throw BackupFormatException("Bad entry $name")
                        target.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                written += n
                                if (written > maxBytes) throw BackupFormatException("Backup too large")
                                out.write(buffer, 0, n)
                            }
                        }
                        unpacked += target.name
                    }
                    else -> Unit
                }
                zip.closeEntry()
            }
        }
        val found = manifest ?: throw BackupFormatException("Not a Super Doc backup")
        return found.copy(items = found.items.filter { it.file == null || it.file in unpacked })
    }

    private fun readCapped(input: InputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > max) throw BackupFormatException("Manifest too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun toJson(manifest: BackupManifest): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("createdAt", manifest.createdAt)
        .put(
            "settings",
            JSONObject()
                .put("signatureName", manifest.settings.signatureName)
                .put("familyName", manifest.settings.familyName)
                .put("familyPhone", manifest.settings.familyPhone),
        )
        .put(
            "items",
            JSONArray().apply {
                manifest.items.forEach { item ->
                    put(
                        JSONObject()
                            .put("type", item.type)
                            .put("title", item.title)
                            .put("titleIsAuto", item.titleIsAuto)
                            .put("body", item.body)
                            .put("file", item.file ?: JSONObject.NULL)
                            .put("pageCount", item.pageCount)
                            .put("createdAt", item.createdAt)
                            .put("updatedAt", item.updatedAt),
                    )
                }
            },
        )

    fun parse(json: String): BackupManifest {
        val root = runCatching { JSONObject(json) }.getOrElse { throw BackupFormatException("Bad manifest") }
        if (root.optString("format") != FORMAT) throw BackupFormatException("Not a Super Doc backup")
        if (root.optInt("version") > VERSION) throw BackupFormatException("Backup from a newer app")
        val settings = root.optJSONObject("settings")
        val items = root.optJSONArray("items") ?: JSONArray()
        return BackupManifest(
            createdAt = root.optLong("createdAt"),
            settings = BackupSettings(
                signatureName = settings?.optString("signatureName").orEmpty(),
                familyName = settings?.optString("familyName").orEmpty(),
                familyPhone = settings?.optString("familyPhone").orEmpty(),
            ),
            items = (0 until items.length()).mapNotNull { i ->
                val o = items.optJSONObject(i) ?: return@mapNotNull null
                val type = o.optString("type")
                if (type !in TYPES) return@mapNotNull null
                val file = if (o.isNull("file")) null else o.optString("file")
                if (file != null && !isSafeFileName(file)) return@mapNotNull null
                if (type != "NOTE" && file == null) return@mapNotNull null
                BackupItem(
                    type = type,
                    title = o.optString("title").take(200),
                    titleIsAuto = o.optBoolean("titleIsAuto", true),
                    body = o.optString("body"),
                    file = file,
                    pageCount = o.optInt("pageCount").coerceAtLeast(0),
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt"),
                )
            },
        )
    }

    private val TYPES = setOf("NOTE", "PDF", "DOCX")
}
