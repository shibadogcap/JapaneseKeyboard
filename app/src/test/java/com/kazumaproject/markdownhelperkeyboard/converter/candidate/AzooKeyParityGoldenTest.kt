package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount
import com.kazumaproject.markdownhelperkeyboard.converter.api.prefixComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AzooKey 本家 parity golden（Zenz/学習無効、同梱 LOUDS + cb/mm）。
 */
class AzooKeyParityGoldenTest {
    @Test
    fun nihonTopFiveMatchesAzooKey() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyParityGoldenFixtures.engine()
        val result = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest("にほん"),
        )
        assertEquals("日本", result.mainResults.firstOrNull()?.string)
        assertTrue(result.mainResults.take(5).any { it.string == "日本" })
        AzooKeyParityGoldenFixtures.assertTopThreeContainsExactReading(result.mainResults, "にほん")
    }

    @Test
    fun shikaiTopFiveMatchesAzooKey() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyParityGoldenFixtures.engine()
        val result = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest("しかい"),
        )
        assertEquals(
            listOf("視界", "司会", "歯科医", "市会", "士会"),
            result.mainResults.take(5).map { it.string },
        )
        AzooKeyParityGoldenFixtures.assertTopThreeContainsExactReading(result.mainResults, "しかい")
    }

    @Test
    fun mixed2000EnUsesNaturalLatticeRanking() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyParityGoldenFixtures.engine()
        val result = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest("2000えん"),
        )
        assertEquals("2000えん", result.mainResults.firstOrNull()?.string)
        AzooKeyParityGoldenFixtures.assertTopThreeContainsExactReading(result.mainResults, "2000えん")
    }

    @Test
    fun mixed2000EnKatakanaUsesNaturalLatticeRanking() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyParityGoldenFixtures.engine()
        val result = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest("2000エン"),
        )
        val top = result.mainResults.firstOrNull()?.string.orEmpty()
        assertTrue("Expected number + en reading, got $top", top.startsWith("2000") && top.endsWith("えん"))
        AzooKeyParityGoldenFixtures.assertTopThreeContainsExactReading(result.mainResults, "2000エン")
    }

    @Test
    fun longInputDoesNotExplodeCandidateCount() = AzooKeyParityGoldenFixtures.runWithAssets {
        val longInput = "きょうはとてもいいてんきでさんぽにいきたいとおもいます"
        val engine = AzooKeyParityGoldenFixtures.engine(nBest = 10)
        val result = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest(longInput, nBest = 10),
        )
        assertTrue("Visible candidates should stay bounded, got ${result.mainResults.size}", result.mainResults.size <= 200)
        assertTrue(result.mainResults.isNotEmpty())
        assertTrue(result.mainResults.first().string.isNotBlank())
    }

    @Test
    fun afterCompleteReusesLatticeWithCompletedData() = AzooKeyParityGoldenFixtures.runWithAssets {
        val session = ConversionSession()
        val engine = AzooKeyParityGoldenFixtures.engine()
        val fullInput = "きょうはいいてんき"
        val first = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest(fullInput),
            session = session,
        )
        val firstClause = first.firstClauseResults.firstOrNull()
            ?: first.mainResults.firstOrNull()
            ?: error("Expected first clause candidate")
        val previousComposing = ComposingText.fromConvertTarget(fullInput)
        val remainder = "はいいてんき"
        val completedSurfaceLength = firstClause.data
            .take((firstClause.data.indices.firstOrNull() ?: -1) + 1)
            .sumOf { it.reading.length }
            .takeIf { it > 0 }
            ?: firstClause.rubyCount
            ?: firstClause.string.length
        val trimmed = previousComposing.prefixComplete(
            composingCount = ComposingCount.SurfaceCount(completedSurfaceLength),
        )
        assertEquals(remainder, trimmed.convertTarget)

        val secondResponse = AzooKeyParityGoldenFixtures.convertWithEngine(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest(
                input = remainder,
                composingText = trimmed,
                completedCandidate = firstClause,
            ),
            session = session,
        )
        assertTrue(secondResponse.conversionResult.mainResults.isNotEmpty())
        assertTrue("Expected afterComplete path", secondResponse.usedAfterComplete)
    }

    @Test
    fun romanIttaiProducesKatakanaReading() = AzooKeyParityGoldenFixtures.runWithAssets {
        val composing = AzooKeyParityGoldenFixtures.romanIttaiComposingText()
        assertEquals("いったい", composing.convertTarget)
        val engine = AzooKeyParityGoldenFixtures.engine()
        val result = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest(
                input = composing.convertTarget,
                composingText = composing,
            ),
        )
        assertTrue(result.mainResults.isNotEmpty())
        val topYomi = result.mainResults.first().yomi?.hiraganaToKatakana().orEmpty()
        assertTrue(
            "Expected イッタイ reading in top candidate, got $topYomi",
            topYomi.contains("イッタイ") || topYomi.contains("イッテ"),
        )
    }

    @Test
    fun zenzaiModeReordersTopFiveByValue() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyParityGoldenFixtures.engine()
        val request = AzooKeyParityGoldenFixtures.defaultRequest("しかい").copy(
            zenzaiMode = AzooKeyStyleZenzaiMode.On,
        )
        val result = AzooKeyParityGoldenFixtures.convert(engine, request)
        val sentenceCandidates = result.mainResults
            .filter { it.type == CandidateType.NBEST }
            .take(5)
        assertTrue(sentenceCandidates.isNotEmpty())
        val values = sentenceCandidates.map { it.value }
        assertEquals(values.sortedDescending(), values)
    }

}
