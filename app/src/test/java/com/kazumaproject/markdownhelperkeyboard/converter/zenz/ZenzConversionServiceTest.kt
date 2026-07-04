package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzConversionServiceTest {
    private class FakeZenzEngine(
        var generateResult: String = "テスト",
        var evaluateResult: String = "PASS",
        var scoreResult: FloatArray = floatArrayOf(1f, 0.5f),
    ) : ZenzEnginePort {
        override suspend fun generateWithContext(
            profile: String,
            leftContext: String,
            inputKatakana: String,
            maxTokens: Int,
        ): String = generateResult

        override suspend fun candidateEvaluate(
            profile: String,
            leftContext: String,
            inputKatakana: String,
            candidate: String,
        ): String = evaluateResult

        override suspend fun scoreCandidates(
            profile: String,
            leftContext: String,
            inputKatakana: String,
            candidates: List<String>,
        ): FloatArray = scoreResult
    }

    private fun policy(allows: Boolean = true) = AzooKeyRuntimeConversionPolicy(
        allowsPersonalizedConversion = allows,
        learningType = AzooKeyStyleLearningType.Nothing,
        zenzaiMode = AzooKeyStyleZenzaiMode.Off,
        experimentalZenzaiPredictiveInput = false,
        liveConversionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode.Disabled,
    )

    @Test
    fun shouldGenerateRequiresValidReading() {
        val service = ZenzConversionService(FakeZenzEngine())
        val config = ZenzConversionConfig(profile = "test")
        val request = ZenzGenerationRequest(
            insertReading = "とう",
            leftContext = "",
            config = config,
        )
        assertTrue(service.shouldGenerate(request, policy()))
        assertFalse(service.shouldGenerate(request.copy(insertReading = "a"), policy()))
        assertFalse(service.shouldGenerate(request, policy(allows = false)))
    }

    @Test
    fun evaluateZenzaiWholeResultReturnsGeneratedSurface() = runTest {
        val service = ZenzConversionService(FakeZenzEngine(evaluateResult = "WHOLE:修正結果"))
        val results = service.evaluateZenzai(
            ZenzPredictiveRequest(
                insertReading = "てすと",
                leftContext = "",
                dictionaryCandidates = listOf(
                    Candidate(
                        string = "テスト",
                        type = CandidateType.NBEST,
                        length = 3u,
                        score = 0,
                        yomi = "テスト",
                    ),
                ),
                nBest = 4,
                config = ZenzConversionConfig(profile = "test"),
            ),
        )
        assertEquals("修正結果", results.single().string)
    }

    @Test
    fun evaluateZenzaiFixRequiredUsesDictionaryPrefix() = runTest {
        val service = ZenzConversionService(FakeZenzEngine(evaluateResult = "FIX:東京都"))
        val results = service.evaluateZenzai(
            ZenzPredictiveRequest(
                insertReading = "とうきょう",
                leftContext = "",
                dictionaryCandidates = listOf(
                    Candidate(string = "東京駅", type = CandidateType.NBEST, length = 3u, score = 0, yomi = "トウキョウエキ"),
                    Candidate(string = "東京都庁", type = CandidateType.NBEST, length = 4u, score = 0, yomi = "トウキョウトチョウ"),
                ),
                nBest = 4,
                config = ZenzConversionConfig(profile = "test"),
            ),
        )
        assertEquals("東京都庁", results.single().string)
    }

    @Test
    fun rerankReturnsNullWhenZenzaiModeEnabled() = runTest {
        val service = ZenzConversionService(FakeZenzEngine())
        val result = service.rerank(
            ZenzRerankRequest(
                insertReading = "てすと",
                leftContext = "",
                candidates = listOf(
                    Candidate(string = "テスト", type = CandidateType.NBEST, length = 3u, score = 10, yomi = "テスト"),
                    Candidate(string = "テスト2", type = CandidateType.NBEST, length = 3u, score = 9, yomi = "テスト"),
                ),
                config = ZenzConversionConfig(profile = "test", rerankEnabled = true),
            ),
            policy().copy(zenzaiMode = AzooKeyStyleZenzaiMode.On),
        )
        assertNull(result)
    }
}
