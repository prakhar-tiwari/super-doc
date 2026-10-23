package com.superdoc.app.ui.note

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteParagraphsTest {
    private fun parts(text: String) = NoteParagraphs.ranges(text).map { text.substring(it) }

    @Test
    fun eachLineIsAParagraphAndBlankLinesAreSkipped() {
        assertEquals(listOf("दूध लाना है", "Doctor at 10", "Call Asha"), parts("दूध लाना है\nDoctor at 10\n\n  \nCall Asha\n"))
    }

    @Test
    fun emptyAndBlankNotesHaveNoParagraphs() {
        assertEquals(emptyList<String>(), parts(""))
        assertEquals(emptyList<String>(), parts("\n \n"))
    }

    @Test
    fun rangesPointIntoTheText() {
        val text = "one\n\ntwo"
        assertEquals(listOf(0..2, 5..7), NoteParagraphs.ranges(text))
    }
}
