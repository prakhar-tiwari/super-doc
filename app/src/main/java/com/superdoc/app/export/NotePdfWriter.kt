package com.superdoc.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.superdoc.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws a [FormattedDoc] onto A4 pages with Android's own text engine (StaticLayout),
 * which shapes Devanagari correctly (conjuncts, matras, reph). PdfBox cannot do this.
 */
@Singleton
class NotePdfWriter @Inject constructor(@ApplicationContext private val context: Context) {

    private val regular: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.noto_sans_devanagari_regular) ?: Typeface.DEFAULT
    }
    private val bold: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.noto_sans_devanagari_bold) ?: Typeface.DEFAULT_BOLD
    }

    suspend fun write(doc: FormattedDoc, out: File): Int = withContext(Dispatchers.Default) {
        val pdf = PdfDocument()
        try {
            val pages = Paginator(pdf).apply { doc.blocks.forEach { draw(it) } }.finish()
            out.outputStream().use { pdf.writeTo(it) }
            pages
        } finally {
            pdf.close()
        }
    }

    private inner class Paginator(private val pdf: PdfDocument) {
        private val contentWidth = (PAGE_WIDTH - 2 * MARGIN).toInt()
        private val bottom = PAGE_HEIGHT - MARGIN - FOOTER
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var y = MARGIN

        private val body = paint(regular, BODY_SIZE)
        private val bodyBold = paint(bold, BODY_SIZE)
        private val title = paint(bold, TITLE_SIZE)
        private val footer = paint(regular, 9f).apply { color = Color.DKGRAY; textAlign = Paint.Align.CENTER }

        private fun paint(face: Typeface, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            color = Color.BLACK
        }

        private fun canvas(): Canvas {
            page?.let { return it.canvas }
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber).create()
            return pdf.startPage(info).also { page = it; y = MARGIN }.canvas
        }

        private fun endPage() {
            val p = page ?: return
            p.canvas.drawText("— $pageNumber —", PAGE_WIDTH / 2, PAGE_HEIGHT - MARGIN / 2, footer)
            pdf.finishPage(p)
            page = null
        }

        fun draw(block: Block) {
            when (block) {
                is Block.Title -> text(block.text, title, spacingAfter = 14f)
                is Block.Paragraph -> text(
                    block.text,
                    if (block.bold) bodyBold else body,
                    align = if (block.align == Align.END) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL,
                )
                is Block.Bullet -> text(block.text, body, marker = "•")
                is Block.Numbered -> text(block.text, body, marker = "${block.number}.")
                Block.Spacer -> { canvas(); y += BODY_SIZE }
            }
        }

        private fun text(
            text: String,
            paint: TextPaint,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
            marker: String? = null,
            spacingAfter: Float = 8f,
        ) {
            val indent = if (marker != null) INDENT else 0f
            val width = contentWidth - indent.toInt()
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(align)
                .setLineSpacing(0f, LINE_SPACING)
                .setIncludePad(true)
                .build()
            var line = 0
            var first = true
            while (line < layout.lineCount) {
                var c = canvas()
                if (y + lineHeight(layout, line) > bottom && y > MARGIN) {
                    endPage()
                    c = canvas()
                }
                // As many lines as fit on this page.
                var last = line
                while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - layout.getLineTop(line)) <= bottom) last++
                val top = layout.getLineTop(line).toFloat()
                val height = layout.getLineBottom(last) - top
                if (first && marker != null) {
                    c.drawText(marker, MARGIN, y - top + layout.getLineBaseline(line), paint)
                }
                c.save()
                c.translate(MARGIN + indent, y - top)
                c.clipRect(0f, top, width.toFloat(), top + height)
                layout.draw(c)
                c.restore()
                y += height
                line = last + 1
                first = false
            }
            y += spacingAfter
        }

        private fun lineHeight(layout: StaticLayout, line: Int) =
            (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()

        fun finish(): Int {
            if (page == null && pageNumber == 0) canvas()
            endPage()
            return pageNumber
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595f // A4 in points
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 56f
        const val FOOTER = 16f
        const val INDENT = 22f
        const val BODY_SIZE = 13f
        const val TITLE_SIZE = 20f
        const val LINE_SPACING = 1.3f
    }
}
