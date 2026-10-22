package com.superdoc.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentMatcherTest {
    private fun ai(a: AiAction) = VoiceIntent.Ai(a)

    @Test
    fun `english instructions`() {
        assertEquals(ai(AiAction.FIX_SPELLING), IntentMatcher.match("Please fix the mistakes"))
        assertEquals(ai(AiAction.MAKE_LETTER), IntentMatcher.match("make it a letter to the bank"))
        assertEquals(ai(AiAction.TRANSLATE), IntentMatcher.match("Translate this into Hindi"))
        assertEquals(ai(AiAction.MAKE_SHORTER), IntentMatcher.match("make it short"))
        assertEquals(ai(AiAction.MAKE_NEAT), IntentMatcher.match("make it neat"))
        assertEquals(VoiceIntent.ExportPdf, IntentMatcher.match("make a PDF"))
        assertEquals(VoiceIntent.ExportWord, IntentMatcher.match("I want a Word file"))
        assertEquals(VoiceIntent.ReadAloud, IntentMatcher.match("read it to me"))
        assertEquals(VoiceIntent.Send, IntentMatcher.match("send it on WhatsApp"))
    }

    @Test
    fun `hindi instructions`() {
        assertEquals(ai(AiAction.FIX_SPELLING), IntentMatcher.match("इसकी गलतियाँ ठीक करो"))
        assertEquals(ai(AiAction.FIX_SPELLING), IntentMatcher.match("ग़लतियां सुधार दो"))
        assertEquals(ai(AiAction.MAKE_LETTER), IntentMatcher.match("बैंक के लिए पत्र बना दो"))
        assertEquals(ai(AiAction.MAKE_LETTER), IntentMatcher.match("एक एप्लीकेशन लिख दो"))
        assertEquals(ai(AiAction.TRANSLATE), IntentMatcher.match("इसे अंग्रेज़ी में कर दो"))
        assertEquals(ai(AiAction.MAKE_SHORTER), IntentMatcher.match("थोड़ा छोटा कर दो"))
        assertEquals(VoiceIntent.ExportPdf, IntentMatcher.match("पीडीएफ बना दो"))
        assertEquals(VoiceIntent.ReadAloud, IntentMatcher.match("पढ़कर सुनाओ"))
        assertEquals(VoiceIntent.Send, IntentMatcher.match("बेटे को भेज दो"))
    }

    @Test
    fun `precomposed and decomposed nukta letters match the same`() {
        val precomposed = "\u092A\u095D\u0915\u0930 \u0938\u0941\u0928\u093E\u0913" // पढ़कर with U+095D
        assertEquals(VoiceIntent.ReadAloud, IntentMatcher.match(precomposed))
    }

    @Test
    fun `specific request wins over export`() {
        assertEquals(ai(AiAction.TRANSLATE), IntentMatcher.match("translate into English and make a PDF"))
    }

    @Test
    fun `english keywords match whole words only`() {
        assertEquals(VoiceIntent.Unknown, IntentMatcher.match("swordfish"))
        assertEquals(VoiceIntent.Unknown, IntentMatcher.match("what is the weather"))
        assertEquals(VoiceIntent.Unknown, IntentMatcher.match("   "))
    }

    @Test
    fun `hindi written in english letters is understood`() {
        assertEquals(ai(AiAction.MAKE_NEAT), IntentMatcher.match("isko thoda saaf kar do"))
        assertEquals(ai(AiAction.FIX_SPELLING), IntentMatcher.match("galtiyan theek karo"))
        assertEquals(ai(AiAction.MAKE_LETTER), IntentMatcher.match("bank ke liye chitthi bana do"))
        assertEquals(VoiceIntent.Send, IntentMatcher.match("beta ko bhej do"))
    }

    @Test
    fun `model answers are parsed`() {
        assertEquals(ai(AiAction.MAKE_LETTER), IntentMatcher.fromModelAnswer(" LETTER."))
        assertEquals(VoiceIntent.Send, IntentMatcher.fromModelAnswer("send"))
        assertEquals(VoiceIntent.Unknown, IntentMatcher.fromModelAnswer("NONE"))
        assertEquals(VoiceIntent.Unknown, IntentMatcher.fromModelAnswer("मुझे नहीं पता"))
    }
}
