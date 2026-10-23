package com.superdoc.app.ui.note

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/** Each line the user ends with Enter is a paragraph; blank lines are gaps between them. */
object NoteParagraphs {
    fun ranges(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            if (text.substring(start, end).isNotBlank()) out += start until end
            start = end + 1
        }
        return out
    }
}

/**
 * Soft backgrounds with a darker stripe on the left. Text stays near-black, so contrast stays above
 * 14:1 on every band; the stripes also differ in lightness, so neighbours stay apart for colour-blind readers.
 */
private val BANDS = listOf(
    Color(0xFFE3F2FD) to Color(0xFF1565C0),
    Color(0xFFFFF3E0) to Color(0xFFE65100),
    Color(0xFFE8F5E9) to Color(0xFF2E7D32),
    Color(0xFFF3E5F5) to Color(0xFF7B1FA2),
)

/** Draws a coloured band behind each paragraph of [text], using the field's own text layout. */
fun Modifier.paragraphBands(text: String, layout: () -> TextLayoutResult?, enabled: Boolean): Modifier =
    if (!enabled) this else drawBehind {
        val result = layout() ?: return@drawBehind
        if (result.layoutInput.text.text != text) return@drawBehind
        val outside = 12.dp.toPx()
        val stripe = 4.dp.toPx()
        val gap = 1.5.dp.toPx()
        val radius = CornerRadius(6.dp.toPx())
        NoteParagraphs.ranges(text).forEachIndexed { i, range ->
            val (band, edge) = BANDS[i % BANDS.size]
            val top = result.getLineTop(result.getLineForOffset(range.first)) + gap
            val bottom = result.getLineBottom(result.getLineForOffset(range.last)) - gap
            val left = -outside
            drawRoundRect(band, Offset(left, top), Size(size.width + outside * 2, bottom - top), radius)
            drawRoundRect(edge, Offset(left, top), Size(stripe, bottom - top), radius)
        }
    }
