package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Engine 直叩き golden（Lattice primary + 同梱 cb/mm）。
 */
class AzooKeyLatticePrimaryGoldenTest {
    @Test
    fun engineMatchesAzooKeyGoldenTopFiveWhenConnectionAssetsPresent() = AzooKeyParityGoldenFixtures.runWithAssets {

        val engine = AzooKeyParityGoldenFixtures.engine(nBest = 40)
        val request = AzooKeyParityGoldenFixtures.defaultRequest("しかい", nBest = 40)
        val mainCandidates = AzooKeyParityGoldenFixtures.convert(engine, request)
        assertEquals(
            listOf("視界", "司会", "歯科医", "市会", "士会"),
            mainCandidates.mainResults.take(5).map { it.string },
        )

        val areResult = AzooKeyParityGoldenFixtures.convert(
            engine,
            request.copy(input = "あれ", japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix),
        )
        assertTrue(areResult.mainResults.isNotEmpty())

        val madaResult = AzooKeyParityGoldenFixtures.convert(
            engine,
            request.copy(input = "まだ", japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix),
        )
        assertTrue(madaResult.mainResults.isNotEmpty())
    }

    @Test
    fun mixedInputConversionWorksForNumbersAndKana() = AzooKeyParityGoldenFixtures.runWithAssets {

        val engine = AzooKeyParityGoldenFixtures.engine(nBest = 40)
        val mainCandidates = AzooKeyParityGoldenFixtures.convert(
            engine,
            AzooKeyParityGoldenFixtures.defaultRequest("2000えん", nBest = 40),
        )
        assertEquals("2000円", mainCandidates.mainResults.firstOrNull()?.string)
        assertTrue(mainCandidates.mainResults.isNotEmpty())
    }
}
