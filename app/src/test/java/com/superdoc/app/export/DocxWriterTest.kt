package com.superdoc.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocxWriterTest {

    private fun entries(doc: FormattedDoc): Map<String, String> {
        val bytes = ByteArrayOutputStream().also { DocxWriter.write(doc, it) }.toByteArray()
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return result
    }

    @Test
    fun `package contains the parts Word requires`() {
        val parts = entries(FormattedDoc(listOf(Block.Paragraph("Hi")), hindi = false)).keys
        assertTrue(parts.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml")))
    }

    @Test
    fun `every part is well-formed XML and hindi text survives`() {
        val doc = FormattedDoc(
            listOf(
                Block.Title("सूची & <items>"),
                Block.Paragraph("पहली लाइन\nदूसरी लाइन", Align.END, bold = true),
                Block.Bullet("दूध"),
                Block.Numbered(1, "क्षत्रिय \"quote\" 'x'"),
                Block.Spacer,
            ),
            hindi = true,
        )
        val parts = entries(doc)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        parts.forEach { (name, xml) ->
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
                .also { assertTrue("$name has a root", it.documentElement != null) }
        }
        val body = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(parts.getValue("word/document.xml").toByteArray(Charsets.UTF_8)))
        val text = (0 until body.getElementsByTagNameNS("*", "t").length)
            .joinToString("|") { body.getElementsByTagNameNS("*", "t").item(it).textContent }
        assertEquals("सूची & <items>|पहली लाइन|दूसरी लाइन|•|दूध|1.|क्षत्रिय \"quote\" 'x'", text)
        assertTrue(parts.getValue("word/document.xml").contains("""<w:jc w:val="right"/>"""))
        assertTrue(parts.getValue("word/styles.xml").contains("""w:cs="Nirmala UI""""))
    }

    @Test
    fun `control characters are dropped`() {
        assertEquals("ab", DocxWriter.escape("a\u0001b"))
    }
}
