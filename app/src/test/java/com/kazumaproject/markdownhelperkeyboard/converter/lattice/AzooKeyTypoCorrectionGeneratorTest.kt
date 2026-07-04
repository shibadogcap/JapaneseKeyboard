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

    @Test
    fun classGeneratorCollectsTypoReadingsForDirectInput() {
        val composing = com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText.fromConvertTarget("たいかくせい")
        val readings = AzooKeyTypoCorrectionGenerator.collectTypoReadings(
            composingText = composing,
            surfaceStart = 0,
            surfaceEndExclusive = composing.convertTarget.length,
        )
        assertTrue(readings.any { it.katakana.contains("ダ") || it.katakana.contains("ガ") })
    }

    @Test
    fun setUnreachablePathPrunesStablePrefix() {
        val composing = com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText.fromConvertTarget("たいかくせい")
        val range = AzooKeyTypoCorrectionGenerator.inputRangeForSurface(
            composingText = composing,
            surfaceStart = 0,
            surfaceEndExclusive = composing.convertTarget.length,
        ) ?: return
        val generator = AzooKeyTypoCorrectionGenerator(
            inputs = composing.input,
            range = range,
            roman2Kana = com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.Identity,
        )
        while (true) {
            val next = generator.next() ?: break
            if (next.penalty == 0f && next.katakana == "タイ") {
                generator.setUnreachablePath("タイ")
                break
            }
        }
        val readings = buildList {
            while (true) {
                add(generator.next() ?: break)
            }
        }
        assertTrue(readings.none { it.katakana.startsWith("タイ") && it.penalty > 0 })
    }
}
