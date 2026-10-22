package com.superdoc.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormatterTest {

    @Test
    fun `blank lines separate paragraphs and line breaks are kept`() {
        val blocks = NoteFormatter.body("Line one\nline two\n\nSecond para")
        assertEquals(listOf(Block.Paragraph("Line one\nline two"), Block.Paragraph("Second para")), blocks)
    }

    @Test
    fun `bullets and numbers are recognised`() {
        val blocks = NoteFormatter.body("Buy:\n- milk\n• bread\n1. call doctor\n2) pay bill")
        assertEquals(
            listOf(
                Block.Paragraph("Buy:"),
                Block.Bullet("milk"),
                Block.Bullet("bread"),
                Block.Numbered(1, "call doctor"),
                Block.Numbered(2, "pay bill"),
            ),
            blocks,
        )
    }

    @Test
    fun `list uses a heading only when the first line looks like one`() {
        assertEquals(
            listOf(Block.Title("Grocery list"), Block.Bullet("Milk"), Block.Bullet("Eggs")),
            NoteFormatter.list("Grocery list\n- Milk\nEggs"),
        )
        assertEquals(
            listOf(Block.Bullet("Milk"), Block.Bullet("Eggs")),
            NoteFormatter.list("Milk\nEggs"),
        )
        assertEquals(Block.Title("दवाइयों की सूची"), NoteFormatter.list("दवाइयों की सूची\nपैरासिटामोल")[0])
    }

    @Test
    fun `hindi letter uses hindi words`() {
        val doc = NoteFormatter.format(
            text = "मेरा मोबाइल नंबर बदल दें।",
            template = Template.LETTER,
            hindi = true,
            date = "22 अक्टू॰ 2026",
            fields = LetterFields(to = "शाखा प्रबंधक", subject = "नंबर बदलना", signer = "रमेश"),
        )
        val texts = doc.blocks.filterIsInstance<Block.Paragraph>().map { it.text }
        assertEquals("दिनांक: 22 अक्टू॰ 2026", texts.first())
        assertTrue("सेवा में," in texts)
        assertTrue("विषय: नंबर बदलना" in texts)
        assertTrue("भवदीय," in texts)
        assertEquals("रमेश", texts.last())
        assertEquals(Align.END, (doc.blocks.first() as Block.Paragraph).align)
    }

    @Test
    fun `english letter skips empty fields`() {
        val doc = NoteFormatter.format("Please help.", Template.LETTER, hindi = false, date = "22 Oct 2026")
        val texts = doc.blocks.filterIsInstance<Block.Paragraph>().map { it.text }
        assertEquals(listOf("Date: 22 Oct 2026", "Respected Sir/Madam,", "Please help.", "Yours faithfully,"), texts)
    }
}
