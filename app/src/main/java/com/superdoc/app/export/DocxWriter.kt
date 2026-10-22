package com.superdoc.app.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A tiny Word (.docx) writer: just the parts Word, Google Docs and WPS need.
 * Apache POI would add several MB and is slow to start on budget phones.
 * Hindi text uses the complex-script font slot ("Nirmala UI", present on Windows 8+;
 * Mangal is the fallback on older Windows).
 */
object DocxWriter {
    private const val NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    fun write(doc: FormattedDoc, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", CONTENT_TYPES)
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("word/_rels/document.xml.rels", DOCUMENT_RELS)
            zip.entry("word/styles.xml", STYLES)
            zip.entry("word/document.xml", documentXml(doc))
        }
    }

    fun documentXml(doc: FormattedDoc): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="$NS"><w:body>""")
        doc.blocks.forEach { block ->
            when (block) {
                is Block.Title -> paragraph(block.text, bold = true, sizeHalfPoints = 32, spacingAfter = 240)
                is Block.Paragraph -> paragraph(block.text, bold = block.bold, align = block.align)
                is Block.Bullet -> paragraph("•\t${block.text}", indent = true)
                is Block.Numbered -> paragraph("${block.number}.\t${block.text}", indent = true)
                Block.Spacer -> append("<w:p/>")
            }
        }
        append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>""")
        append("""<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>""")
        append("</w:body></w:document>")
    }

    private fun StringBuilder.paragraph(
        text: String,
        bold: Boolean = false,
        align: Align = Align.START,
        indent: Boolean = false,
        sizeHalfPoints: Int? = null,
        spacingAfter: Int = 120,
    ) {
        append("<w:p><w:pPr>")
        append("""<w:spacing w:after="$spacingAfter" w:line="300" w:lineRule="auto"/>""")
        if (indent) append("""<w:tabs><w:tab w:val="left" w:pos="360"/></w:tabs><w:ind w:left="360" w:hanging="360"/>""")
        if (align == Align.END) append("""<w:jc w:val="right"/>""")
        append("</w:pPr>")
        val runProps = buildString {
            append("<w:rPr>")
            if (bold) append("<w:b/><w:bCs/>")
            if (sizeHalfPoints != null) append("""<w:sz w:val="$sizeHalfPoints"/><w:szCs w:val="$sizeHalfPoints"/>""")
            append("</w:rPr>")
        }
        text.split("\n").forEachIndexed { i, line ->
            append("<w:r>").append(runProps)
            if (i > 0) append("<w:br/>")
            line.split("\t").forEachIndexed { j, part ->
                if (j > 0) append("<w:tab/>")
                append("""<w:t xml:space="preserve">""").append(escape(part)).append("</w:t>")
            }
            append("</w:r>")
        }
        append("</w:p>")
    }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c.code >= 0x20 || c == '\t' || c == '\n' || c == '\r') append(c)
            }
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private val DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="$NS">
<w:docDefaults><w:rPrDefault><w:rPr>
<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Nirmala UI"/>
<w:sz w:val="26"/><w:szCs w:val="26"/><w:lang w:val="en-IN" w:bidi="hi-IN"/>
</w:rPr></w:rPrDefault></w:docDefaults>
</w:styles>"""
}
