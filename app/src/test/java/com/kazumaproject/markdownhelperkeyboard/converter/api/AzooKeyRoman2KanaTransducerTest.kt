package com.kazumaproject.markdownhelperkeyboard.converter.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyRoman2KanaTransducerTest {
    private val transducer = AzooKeyRoman2KanaTransducer.fromMap(
        mapOf(
            "si" to ("し" to 2),
            "ka" to ("か" to 2),
            "i" to ("い" to 1),
            "shi" to ("し" to 3),
            "kai" to ("かい" to 3),
            "shikai" to ("しかい" to 6),
        ),
    )

    @Test
    fun convertAppliesLongestMatch() {
        assertEquals("しかい", transducer.convert("shikai"))
        assertEquals("し", transducer.convert("shi"))
    }

    @Test
    fun insertRoman2KanaAtCursorBuildsConvertTarget() {
        val text = ComposingText.fromConvertTarget("")
            .insertRoman2KanaAtCursor("shikai", transducer)
        assertEquals("しかい", text.convertTarget)
    }

    @Test
    fun mixedDirectAndRoman2KanaSegments() {
        val text = ComposingText.fromConvertTarget("か")
            .insertRoman2KanaAtCursor("shikai", transducer)
        assertEquals("かしかい", text.convertTarget)
    }
}