package com.superdoc.app.util

import java.util.Locale

object Script {
    private fun isDevanagari(c: Char) = c in '\u0900'..'\u097F' || c in '\uA8E0'..'\uA8FF'

    /** True when most letters are Hindi (Devanagari) characters. */
    fun isMostlyHindi(text: String): Boolean {
        var hindi = 0
        var latin = 0
        for (c in text) {
            when {
                isDevanagari(c) -> hindi++
                c in 'a'..'z' || c in 'A'..'Z' -> latin++
            }
        }
        return hindi > 0 && hindi >= latin
    }

    fun localeFor(text: String): Locale =
        if (isMostlyHindi(text)) Locale("hi", "IN") else Locale("en", "IN")
}
