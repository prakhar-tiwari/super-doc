package com.superdoc.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val temp = TemporaryFolder()

    private val note = BackupItem("NOTE", "Shopping list", true, "Milk\nबिस्किट", null, 0, 100, 200)
    private val pdf = BackupItem("PDF", "Electricity bill", false, "", "doc_1.pdf", 2, 300, 400)

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun manifestBytes(vararg items: BackupItem) =
        BackupArchive.toJson(BackupManifest(1, items.toList(), BackupSettings("Ram", "Asha", "98765 43210")))
            .toString().toByteArray()

    @Test
    fun roundTripKeepsItemsFilesAndSettings() {
        val docs = temp.newFolder("docs")
        File(docs, "doc_1.pdf").writeText("%PDF-fake")
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, BackupManifest(1, listOf(note, pdf), BackupSettings("Ram", "Asha", "98765 43210"))) { File(docs, it) }

        val staging = temp.newFolder("staging")
        val back = BackupArchive.read(ByteArrayInputStream(out.toByteArray()), staging, Long.MAX_VALUE)
        assertEquals(listOf(note, pdf), back.items)
        assertEquals("Asha", back.settings.familyName)
        assertEquals("%PDF-fake", File(staging, "doc_1.pdf").readText())
    }

    @Test
    fun zipSlipEntriesAreNeverWrittenOutsideStaging() {
        val parent = temp.newFolder("outer")
        val staging = File(parent, "staging").apply { mkdirs() }
        val zip = zipOf(
            BackupArchive.MANIFEST to manifestBytes(note),
            "files/../evil.pdf" to "x".toByteArray(),
            "../evil.pdf" to "x".toByteArray(),
            "files/sub/evil.pdf" to "x".toByteArray(),
            "/abs/evil.pdf" to "x".toByteArray(),
        )
        BackupArchive.read(ByteArrayInputStream(zip), staging, Long.MAX_VALUE)
        assertFalse(File(parent, "evil.pdf").exists())
        assertTrue(staging.listFiles()!!.isEmpty())
    }

    @Test
    fun manifestCannotPointAtUnsafeNames() {
        assertFalse(BackupArchive.isSafeFileName("../x.pdf"))
        assertFalse(BackupArchive.isSafeFileName("a/b.pdf"))
        assertFalse(BackupArchive.isSafeFileName(".hidden"))
        assertFalse(BackupArchive.isSafeFileName("a..pdf"))
        assertTrue(BackupArchive.isSafeFileName("doc_0b1c-22.pdf"))
        val bad = pdf.copy(file = "../../databases/superdoc.db")
        assertTrue(BackupArchive.parse(String(manifestBytes(bad))).items.isEmpty())
    }

    @Test
    fun itemsWhoseFileIsMissingAreDropped() {
        val zip = zipOf(BackupArchive.MANIFEST to manifestBytes(note, pdf))
        val back = BackupArchive.read(ByteArrayInputStream(zip), temp.newFolder(), Long.MAX_VALUE)
        assertEquals(listOf(note), back.items)
    }

    @Test(expected = BackupFormatException::class)
    fun oversizedContentStops() {
        val zip = zipOf(BackupArchive.MANIFEST to manifestBytes(pdf), "files/doc_1.pdf" to ByteArray(10_000))
        BackupArchive.read(ByteArrayInputStream(zip), temp.newFolder(), maxBytes = 1_000)
    }

    @Test(expected = BackupFormatException::class)
    fun otherZipsAreRejected() {
        val zip = zipOf("hello.txt" to "hi".toByteArray())
        BackupArchive.read(ByteArrayInputStream(zip), temp.newFolder(), Long.MAX_VALUE)
    }
}
