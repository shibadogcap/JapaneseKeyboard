package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyGradualConversionTest {
    @Test
    fun incrementalMatchesFullAtEachStep() = AzooKeyParityGoldenFixtures.runWithAssets {
        val input = "しんだどうぶつ"
        val incrementalEngine = AzooKeyParityGoldenFixtures.engine()
        var text = ""
        for (ch in input) {
            text += ch
            val incremental = AzooKeyParityGoldenFixtures.convert(
                incrementalEngine,
                AzooKeyParityGoldenFixtures.defaultRequest(text),
            ).mainResults.firstOrNull()?.string
            val fullEngine = AzooKeyParityGoldenFixtures.engine()
            val full = AzooKeyParityGoldenFixtures.convert(
                fullEngine,
                AzooKeyParityGoldenFixtures.defaultRequest(text),
            ).mainResults.firstOrNull()?.string
            assertEquals("mismatch at length=${text.length} text=$text", full, incremental)
        }
    }
}
