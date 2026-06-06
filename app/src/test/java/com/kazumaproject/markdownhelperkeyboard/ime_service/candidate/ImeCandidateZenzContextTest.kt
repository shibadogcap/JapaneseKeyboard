package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicyInput
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicyResolver
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeCandidateZenzContextTest {
    private val publicPolicy: AzooKeyRuntimeConversionPolicy =
        AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = AzooKeyStyleLearningType.Nothing,
                zenzaiMode = AzooKeyStyleZenzaiMode.On,
                isComposing = true,
            ),
        )

    @Test
    fun shouldEmitAsyncGenerationMatchesLegacySingleCharInput() {
        val zenz = baseZenz(asyncGenerationEnabled = true, rerankEnabled = false)
        assertTrue(zenz.shouldEmitAsyncGeneration("あ", publicPolicy))
    }

    @Test
    fun shouldEmitAsyncGenerationFalseWhenRerankEnabled() {
        val zenz = baseZenz(asyncGenerationEnabled = true, rerankEnabled = true)
        assertFalse(zenz.shouldEmitAsyncGeneration("しかい", publicPolicy))
    }

    @Test
    fun shouldEmitAsyncZenzaiRequiresRerankAndZenzaiFlags() {
        val zenz = baseZenz(
            zenzaiEvaluationEnabled = true,
            rerankEnabled = true,
        )
        assertTrue(zenz.shouldEmitAsyncZenzai("しかい", publicPolicy))
    }

    @Test
    fun rerankCacheKeyUsesKatakanaNormalizedInput() {
        val hiraganaPlan = planForInput("しかい")
        val katakanaPlan = planForInput("シカイ")
        assertEquals(hiraganaPlan?.cacheKey, katakanaPlan?.cacheKey)
    }

    @Test
    fun rerankCacheKeyDiffersForDistinctReadings() {
        val planA = planForInput("しかい")
        val planB = planForInput("てすと")
        assertNotEquals(planA?.cacheKey, planB?.cacheKey)
    }

    private fun baseZenz(
        asyncGenerationEnabled: Boolean = false,
        zenzaiEvaluationEnabled: Boolean = false,
        rerankEnabled: Boolean = false,
    ): ImeCandidateZenzContext {
        return ImeCandidateZenzContext(
            config = ZenzConversionConfig(),
            leftContext = "ctx",
            zenzEnabled = true,
            zenzaiEvaluationEnabled = zenzaiEvaluationEnabled,
            asyncGenerationEnabled = asyncGenerationEnabled,
            rerankEnabled = rerankEnabled,
            nBest = 4,
        )
    }

    private fun planForInput(input: String): ImeCandidateZenzRerankPlan? {
        val zenz = ImeCandidateZenzContext(
            config = ZenzConversionConfig(rerankEnabled = true),
            leftContext = "left",
            zenzEnabled = true,
            zenzaiEvaluationEnabled = false,
            asyncGenerationEnabled = false,
            rerankEnabled = true,
            nBest = 4,
        )
        val candidates = listOf(
            Candidate(string = "A", type = 1, length = input.length.toUByte(), score = 1),
            Candidate(string = "B", type = 1, length = input.length.toUByte(), score = 2),
        )
        val policy = com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = 4,
            useUserDictionary = false,
            useUserTemplate = false,
            useRomajiCandidates = false,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode.Disabled,
            englishPredictionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.Nothing,
            typoCorrectionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode.Automatic,
            privacy = CandidateRequestPrivacy(),
        ).runtimeConversionPolicy
        return zenz.prepareRerankPlan(input, candidates, policy)
    }
}