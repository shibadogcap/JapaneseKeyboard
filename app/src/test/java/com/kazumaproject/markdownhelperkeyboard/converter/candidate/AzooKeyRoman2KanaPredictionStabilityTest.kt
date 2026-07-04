package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.appendRoman2KanaCharAtEnd
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Swift [ScenarioTests.testRoman2KanaPredictionStabilityKeepsKekoThroughUnresolvedSuffix] 相当。
 */
class AzooKeyRoman2KanaPredictionStabilityTest {
    private val stablePredictionTarget = "あいうえおかきくけこ"
    private val romanSequence = listOf("a", "i", "u", "e", "o", "k", "a", "k", "i", "k", "u", "k", "e")
    private val transducer = AzooKeyParityGoldenFixtures.defaultRoman2KanaTransducer()

    @Test
    fun stablePredictionSurvivesUnresolvedRomanSuffix() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyParityGoldenFixtures.engine()
        var composing = ComposingText.fromConvertTarget("")
        for (key in romanSequence) {
            composing = composing.appendRoman2KanaCharAtEnd(key.single(), transducer)
            AzooKeyParityGoldenFixtures.convert(
                engine = engine,
                request = AzooKeyParityGoldenFixtures.defaultRequest(
                    input = composing.convertTarget,
                    composingText = composing,
                    requireJapanesePrediction = true,
                ).copy(
                    japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
                ),
                swiftAlignedOptions = true,
                roman2KanaTransducer = transducer,
            )
        }
        assertEquals("あいうえおかきくけ", composing.convertTarget)

        val beforeExtraK = AzooKeyParityGoldenFixtures.convert(
            engine = engine,
            request = AzooKeyParityGoldenFixtures.defaultRequest(
                input = composing.convertTarget,
                composingText = composing,
                requireJapanesePrediction = true,
            ).copy(japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix),
            swiftAlignedOptions = true,
            roman2KanaTransducer = transducer,
        )
        assertEquals(stablePredictionTarget, beforeExtraK.mainResults.firstOrNull()?.string)

        composing = composing.appendRoman2KanaCharAtEnd('k', transducer)
        assertEquals("あいうえおかきくけk", composing.convertTarget)

        val afterExtraK = AzooKeyParityGoldenFixtures.convert(
            engine = engine,
            request = AzooKeyParityGoldenFixtures.defaultRequest(
                input = composing.convertTarget,
                composingText = composing,
                requireJapanesePrediction = true,
            ).copy(japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix),
            swiftAlignedOptions = true,
            roman2KanaTransducer = transducer,
        )
        val allCandidates = (afterExtraK.mainResults + afterExtraK.predictionResults).map { it.string }
        assertTrue(
            "expected stable prediction after unresolved suffix k, got main=${afterExtraK.mainResults.map { it.string }} pred=${afterExtraK.predictionResults.map { it.string }}",
            stablePredictionTarget in allCandidates,
        )
    }
}
