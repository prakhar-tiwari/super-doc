package com.superdoc.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class AutoNamerTest {
    private val time = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 22, 10, 0) }.timeInMillis

    @Test
    fun `uses first meaningful line and date`() {
        val title = AutoNamer.title("Note", "\n\n  Doctor appointment on Monday at 10\nBring reports", time, Locale.ENGLISH)
        assertEquals("Note - Doctor appointment on Monday at… - 22 Oct 2026", title)
    }

    @Test
    fun `empty text gives prefix and date only`() {
        assertEquals("Scan - 22 Oct 2026", AutoNamer.title("Scan", "   ", time, Locale.ENGLISH))
    }

    @Test
    fun `skips lines without enough letters`() {
        assertEquals("Electricity bill", AutoNamer.snippet("12/10/2026\n---\nElectricity bill"))
    }

    @Test
    fun `works with Hindi text`() {
        assertEquals("दवाई की सूची", AutoNamer.snippet("दवाई की सूची।\nसुबह: 1 गोली"))
    }

    @Test
    fun `strips bullet markers`() {
        assertEquals("Milk", AutoNamer.snippet("- Milk\n- Bread"))
    }

    @Test
    fun `unique adds a number`() {
        assertEquals("A (3)", AutoNamer.unique("A", listOf("A", "A (2)")))
        assertEquals("B", AutoNamer.unique("B", listOf("A")))
    }

    @Test
    fun `safe file name removes characters other apps reject`() {
        assertEquals("Bill 10 2026 copy", FileStore.safeFileName("Bill/10:2026*copy?"))
        assertEquals("SuperDoc", FileStore.safeFileName("  /// "))
    }
}
