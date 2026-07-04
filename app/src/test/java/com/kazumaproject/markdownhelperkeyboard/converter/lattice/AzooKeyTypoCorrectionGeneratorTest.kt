package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyTypoCorrectionGeneratorTest {
    @Test
    fun generatesDakutenTypoReadingForTaika() {
        val readings = AzooKeyTypoCorrectionGenerator.generateKatakanaTypoReadings("タイカクセイ")
        assertTrue(readings.any { it.katakana.contains("ダ") && it.penalty > 0 })
        assertTrue(readings.any { it.katakana.contains("ガ") && it.penalty > 0 })
    }

    @Test
    fun shortPrefixProducesNoTypoReadings() {
        val readings = AzooKeyTypoCorrectionGenerator.generateKatakanaTypoReadings("タイ")
        assertTrue(readings.isEmpty())
    }
}
