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
        var lastPrompt: ZenzPromptContext? = null
            private set
        var lastRequestRich: Boolean = false
            private set

        override suspend fun generateWithContext(
            prompt: ZenzPromptContext,
            inputKatakana: String,
            maxTokens: Int,
        ): String {
            lastPrompt = prompt
            return generateResult
        }

        override suspend fun predictNextInputText(
            prompt: ZenzPromptContext,
            composingText: String,
            count: Int,
            possibleNexts: List<String>,
        ): String = generateResult.take(count)

        override suspend fun candidateEvaluate(
            prompt: ZenzPromptContext,
            inputKatakana: String,
            candidate: String,
            requestRichCandidates: Boolean,
        ): String {
            lastPrompt = prompt
            lastRequestRich = requestRichCandidates
            return evaluateResult
        }

        override suspend fun scoreCandidates(
            prompt: ZenzPromptContext,
            inputKatakana: String,
            candidates: List<String>,
        ): FloatArray {
            lastPrompt = prompt
            return scoreResult
        }
    }

    private fun policy(allows: Boolean = true) = AzooKeyRuntimeConversionPolicy(
        allowsPersonalizedConversion = allows,
        learningType = AzooKeyStyleLearningType.Nothing,
        zenzaiMode = AzooKeyStyleZenzaiMode.Off,
        experimentalZenzaiPredictiveInput = false,
        liveConversionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode.Disabled,
    )

    @Test
    fun forwardsV3PromptMetadata() = runTest {
        val fake = FakeZenzEngine(generateResult = "候補")
        val service = ZenzConversionService(fake)
        val config = ZenzConversionConfig(
            profile = "profile-a",
            topic = "topic-b",
            style = "style-c",
            preference = "pref-d",
        )
        val result = service.getPredictiveReading(
            request = ZenzGenerationRequest(
                insertReading = "てすと",
                leftContext = "左文脈",
                config = config,
            ),
            policy = policy(),
        )
        assertEquals("候補", result)
        assertEquals("profile-a", fake.lastPrompt?.profile)
        assertEquals("topic-b", fake.lastPrompt?.topic)
        assertEquals("style-c", fake.lastPrompt?.style)
        assertEquals("pref-d", fake.lastPrompt?.preference)
        assertEquals("左文脈", fake.lastPrompt?.leftContext)
    }

    @Test
    fun shouldGenerateRejectsShortInput() {
        val service = ZenzConversionService(FakeZenzEngine())
        assertFalse(
            service.shouldGenerate(
                ZenzGenerationRequest("a", leftContext = "", config = ZenzConversionConfig()),
                policy(),
            ),
        )
    }

    @Test
    fun evaluateZenzaiReturnsFallbackOnError() = runTest {
        val service = ZenzConversionService(FakeZenzEngine(evaluateResult = "ERROR"))
        val candidates = service.evaluateZenzai(
            ZenzPredictiveRequest(
                insertReading = "てすと",
                dictionaryCandidates = listOf(
                    Candidate(
                        string = "テスト",
                        type = CandidateType.NBEST,
                        length = 3u,
                        score = 0,
                        value = 0f,
                    ),
                ),
                leftContext = "",
                nBest = 3,
                config = ZenzConversionConfig(),
            ),
        )
        assertEquals(1, candidates.size)
        assertEquals("テスト", candidates.first().string)
    }

    @Test
    fun rerankReturnsNullWhenDisabled() = runTest {
        val service = ZenzConversionService(FakeZenzEngine())
        val result = service.rerank(
            request = ZenzRerankRequest(
                insertReading = "てすと",
                candidates = emptyList(),
                leftContext = "",
                config = ZenzConversionConfig(rerankEnabled = false),
            ),
            policy = policy(),
        )
        assertNull(result)
    }
}
