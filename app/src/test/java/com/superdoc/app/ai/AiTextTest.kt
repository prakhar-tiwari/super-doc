package com.superdoc.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextTest {
    @Test
    fun `cleaner removes chatter, quotes and control tokens`() {
        assertEquals("Tomorrow I go to the doctor.", OutputCleaner.clean("Here is the corrected text:\n\"Tomorrow I go to the doctor.\"<|im_end|>"))
        assertEquals("कल डॉक्टर के पास जाना है।", OutputCleaner.clean("Sure! Here's the corrected version:\nकल डॉक्टर के पास जाना है।\n\nNote: I fixed the spelling."))
        assertEquals("Line one\nLine two", OutputCleaner.clean("```\nLine one\nLine two\n```"))
        assertNull(OutputCleaner.clean("  <end_of_turn> "))
    }

    @Test
    fun `cleaner keeps letter paragraphs that sound like chatter`() {
        val letter = "Dear [Name],\n\nI hope you are well. I am writing about my electricity bill.\n\nPlease let me know.\n\nYours sincerely"
        assertEquals(letter, OutputCleaner.clean(letter))
    }

    @Test
    fun `undecoded tokenizer bytes are turned back into hindi`() {
        // "Ġà¤°" is " र" (space, E0 A4 B0) left in the tokenizer's byte form.
        assertEquals("रमेश को फोन", OutputCleaner.clean("रमेश को\u0120à¤«ोन"))
        assertEquals("café", ByteLevelText.repair("café"))
    }

    @Test
    fun `english sentences get capital letters`() {
        assertEquals("Tomorrow I go. Please call me.\nI'm fine", OutputCleaner.sentenceCase("tomorrow i go. please call me.\ni'm fine"))
        assertEquals("Visit iPhone shop", OutputCleaner.sentenceCase("visit iPhone shop"))
    }

    @Test
    fun `cleaner keeps a first line that was in the note`() {
        val note = "Here is the list: milk, bread"
        assertEquals(note, OutputCleaner.clean(note, original = note))
    }

    @Test
    fun `short text is one chunk`() {
        assertEquals(listOf("hello\nworld"), Chunker.split("hello\nworld", 100))
    }

    @Test
    fun `long text is split at lines and sentences within the limit`() {
        val text = (1..30).joinToString("\n") { "This is line number $it of the note." }
        val chunks = Chunker.split(text, 200)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 200 })
        assertEquals(text.replace("\n", " "), chunks.joinToString("\n").replace("\n", " "))
    }

    @Test
    fun `a very long hindi sentence is cut at spaces`() {
        val text = List(80) { "शब्द" }.joinToString(" ")
        val chunks = Chunker.split(text, 100)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun `translate prompt names the other language`() {
        assertTrue(AiPrompts.build(AiAction.TRANSLATE, "नमस्ते", hindi = true).contains("to English"))
        assertTrue(AiPrompts.build(AiAction.TRANSLATE, "hello", hindi = false).contains("to Hindi"))
        assertTrue(AiPrompts.build(AiAction.FIX_SPELLING, "नमस्ते", hindi = true).contains("Keep it in Hindi"))
    }

    @Test
    fun `catalog parses and rejects insecure entries`() {
        val ok = """{"models":[{"id":"a","name":"A","file":"a.task","url":"https://huggingface.co/x","sha256":"${"a".repeat(64)}",
            "sizeBytes":10,"template":"qwen","minRamMb":2600}]}"""
        val model = ModelCatalog.parse(ok).single()
        assertEquals(ChatTemplate.QWEN, model.template)
        assertEquals(false, model.importOnly)

        val http = ok.replace("https://", "http://")
        assertThrows(IllegalArgumentException::class.java) { ModelCatalog.parse(http) }
        val badSha = ok.replace("a".repeat(64), "1234")
        assertThrows(IllegalArgumentException::class.java) { ModelCatalog.parse(badSha) }
    }

    @Test
    fun `a model in a Play pack is installable without a download address`() {
        val json = """{"models":[{"id":"g","name":"G","file":"g.task","url":null,"sha256":null,"playPack":"gemma_pack",
            "sizeBytes":10,"template":"gemma","minRamMb":2600,"hindi":true}]}"""
        val model = ModelCatalog.parse(json).single()
        assertEquals("gemma_pack", model.playPack)
        assertEquals(false, model.importOnly)
        assertEquals(false, model.directDownload)
    }
}
