package com.superdoc.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Renders a Hindi letter to PDF and compares the rasterised first page with a reviewed golden image.
 * Conjuncts (क्ष, श्र, त्र), the left-side i-matra (कि), reph (र्म) and nukta (ज़) all appear,
 * so a broken shaping engine changes many pixels.
 *
 * To (re)record: delete the golden asset, run the test, then pull
 * /sdcard/Android/data/com.superdoc.app/files/golden/hindi_letter.png into
 * app/src/androidTest/assets/golden/ after checking it by eye.
 */
@RunWith(AndroidJUnit4::class)
class HindiPdfGoldenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val doc = NoteFormatter.format(
        text = "मैं क्षत्रिय परिवार से हूँ। कृपया मेरा आवेदन स्वीकार करें।\n\nज़रूरी काग़ज़:\n- आधार कार्ड\n- श्रमिक कार्ड\n1. धर्म और कर्म",
        template = Template.LETTER,
        hindi = true,
        date = "22 अक्टू॰ 2026",
        fields = LetterFields(to = "शाखा प्रबंधक\nभारतीय स्टेट बैंक", subject = "खाता विवरण हेतु प्रार्थना-पत्र", signer = "रमेश कुमार"),
    )

    private fun render(): Bitmap {
        val pdf = File(context.cacheDir, "golden_test.pdf")
        val pages = runBlocking { NotePdfWriter(context).write(doc, pdf) }
        assertEquals(1, pages)
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bmp = Bitmap.createBitmap(WIDTH, (WIDTH * page.height.toFloat() / page.width).toInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    @Test
    fun hindiLetterMatchesGolden() {
        val actual = render()
        val golden = runCatching {
            instrumentation.context.assets.open("golden/hindi_letter.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        if (golden == null) {
            val dir = File(context.getExternalFilesDir(null), "golden").apply { mkdirs() }
            File(dir, "hindi_letter.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fail("No golden image yet; recorded one at ${dir.absolutePath}/hindi_letter.png. Review it and add it to androidTest assets.")
        }
        golden!!
        assertEquals(golden.width, actual.width)
        assertEquals(golden.height, actual.height)

        var different = 0
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
            val a = actual.getPixel(x, y)
            val g = golden.getPixel(x, y)
            if (abs(Color.red(a) - Color.red(g)) > 48 || abs(Color.green(a) - Color.green(g)) > 48 || abs(Color.blue(a) - Color.blue(g)) > 48) different++
        }
        val ratio = different.toDouble() / (actual.width * actual.height)
        assertTrue("Rendering differs from golden in ${"%.3f".format(ratio * 100)}% of pixels", ratio < MAX_DIFF)
    }

    @Test
    fun pageHasInk() {
        val bmp = render()
        var dark = 0
        for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) if (Color.red(bmp.getPixel(x, y)) < 100) dark++
        assertTrue("Expected visible text on the page", dark > 500)
    }

    private companion object {
        const val WIDTH = 800
        const val MAX_DIFF = 0.005
    }
}
