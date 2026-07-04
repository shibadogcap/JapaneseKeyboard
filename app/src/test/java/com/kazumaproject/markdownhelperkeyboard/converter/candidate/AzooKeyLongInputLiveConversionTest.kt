package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.LiveConversionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyLongInputLiveConversionTest {
    @Test
    fun longInputTopCandidateCoversFullReadingForLiveConversion() = AzooKeyParityGoldenFixtures.runWithAssets {
        val longInput = "きょうはとてもいいてんきでさんぽにいきたい"
        val engine = AzooKeyParityGoldenFixtures.engine(nBest = 10)
        val result = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest(longInput, nBest = 10),
        )
        val top = result.mainResults.firstOrNull()
        requireNotNull(top)
        assertTrue(
            "Top candidate should cover full reading, data=${top.data.sumOf { it.reading.length }} input=${longInput.length}",
            top.data.sumOf { it.reading.length } == longInput.length ||
                top.effectiveRubyCount == longInput.length,
        )

        val manager = LiveConversionManager(enabled = true)
        val liveText = manager.updateWithNewResults(
            composingText = ComposingText.fromConvertTarget(longInput),
            candidates = result.mainResults,
            firstClauseResults = result.firstClauseResults,
            convertTargetCursorPosition = longInput.length,
            convertTarget = longInput,
        )
        assertTrue(liveText.any { it.code > 0x3000 })
        assertEquals(top.string, liveText)
    }
}
