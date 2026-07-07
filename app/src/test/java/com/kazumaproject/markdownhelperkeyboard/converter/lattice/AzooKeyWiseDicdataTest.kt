package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyWiseDicdataTest {
    @Test
    fun generatesLiteralEntryForSingleSymbol() {
        val entries = AzooKeyWiseDicdata.generate(
            convertTarget = "(",
            surfaceRange = 0..0,
            fullText = "(",
        )

        assertTrue(entries.any { it.surface == "(" && it.reading == "(" })
    }

    @Test
    fun generatesLiteralEntryForMixedKanaAndSymbolInputSegment() {
        val entries = AzooKeyWiseDicdata.generate(
            convertTarget = "ア",
            surfaceRange = 1..1,
            fullText = "ア(",
        )

        assertTrue(entries.any { it.surface == "ア" && it.reading == "ア" })
    }
}
