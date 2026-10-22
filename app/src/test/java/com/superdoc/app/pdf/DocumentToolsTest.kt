package com.superdoc.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentToolsTest {
    @Test
    fun `cut in two splits after the chosen page`() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3, 4)), DocumentTools.cutInTwo(pageCount = 5, after = 2))
    }

    @Test
    fun `every page becomes its own part`() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), DocumentTools.everyPage(3))
    }
}
