package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.DefaultRomajiToKanaMap
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AzooKey [InputTablesTests] / [ComposingTextTests] 相当の逐次 roman2kana convertTarget。
 */
class AzooKeyInputTableSequentialTest {
    private val transducer = AzooKeyRoman2KanaTransducer.default()

    private fun sequentialConvertTarget(input: String): String {
        var text = ComposingText.fromConvertTarget("")
        for (ch in input) {
            text = text.appendRoman2KanaCharAtEnd(ch, transducer)
        }
        return text.convertTarget
    }

    @Test
    fun geminationKeepsTrailingConsonantMidInput() {
        assertEquals("い", sequentialConvertTarget("i"))
        assertEquals("いt", sequentialConvertTarget("it"))
        assertEquals("いっt", sequentialConvertTarget("itt"))
        assertEquals("いって", sequentialConvertTarget("itte"))
    }

    @Test
    fun completeGeminationWordsMatchAzooKey() {
        assertEquals("いって", sequentialConvertTarget("itte"))
        assertEquals("いったい", sequentialConvertTarget("ittai"))
    }

    @Test
    fun partialRomanLeavesAsciiTail() {
        assertEquals("あk", sequentialConvertTarget("ak"))
        assertEquals("あき", sequentialConvertTarget("aki"))
    }

    @Test
    fun kantoSequentialMatchesAzooKey() {
        assertEquals("k", sequentialConvertTarget("k"))
        assertEquals("か", sequentialConvertTarget("ka"))
        assertEquals("かn", sequentialConvertTarget("kan"))
        assertEquals("かんt", sequentialConvertTarget("kant"))
        assertEquals("かんと", sequentialConvertTarget("kanto"))
    }

    @Test
    fun nFollowedByNonRomanLeavesNUntilSeparator() {
        val table = AzooKeyInputTable.Default
        val buffer = mutableListOf<Char>()
        table.apply(buffer, 'n')
        table.apply(buffer, 't')
        assertEquals("んt", buffer.joinToString(""))
    }
}
