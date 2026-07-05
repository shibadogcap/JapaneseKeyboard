package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertCandidatesResponse
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRuntimeContext
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionResult
import com.kazumaproject.markdownhelperkeyboard.converter.api.KanaKanjiConverter
import com.kazumaproject.markdownhelperkeyboard.converter.api.PostCompositionPredictionCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionService
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzGenerationRequest
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeCandidateCoordinatorZenzTest {
    @Test
    fun suggestExposesAsyncZenzFlagsAndRerankPlan() = runTest {
        val dictionary = listOf(
            Candidate(string = "司会", type = 1, length = 3u, score = 100),
            Candidate(string = "視界", type = 1, length = 3u, score = 90),
        )
        val coordinator = ImeCandidateCoordinator(
            kanaKanjiConverter = fakeConverter(dictionary),
            zenzConversionService = ZenzConversionService(zenzEngine = FakeZenzEnginePort(generateResult = "生成")),
        )
        val zenz = ImeCandidateZenzContext(
            config = ZenzConversionConfig(rerankEnabled = true),
            leftContext = "ctx",
            zenzEnabled = true,
            zenzaiEvaluationEnabled = false,
            asyncGenerationEnabled = true,
            rerankEnabled = true,
            nBest = 4,
        )
        val result = coordinator.suggest(
            input = "しかい",
            mode = CandidateRequestMode.Normal,
            preferences = testPreferences(),
            zenz = zenz,
        )
        assertNotNull(result.zenzRerankPlan)
        assertEquals(false, result.emitAsyncZenzGeneration)
    }

    @Test
    fun rerankCacheRoundTrip() = runTest {
        val coordinator = ImeCandidateCoordinator(
            kanaKanjiConverter = fakeConverter(emptyList()),
            zenzConversionService = ZenzConversionService(zenzEngine = FakeZenzEnginePort(generateResult = "生成")),
        )
        val plan = ImeCandidateZenzContext(
            config = ZenzConversionConfig(rerankEnabled = true),
            leftContext = "",
            zenzEnabled = true,
            zenzaiEvaluationEnabled = false,
            asyncGenerationEnabled = false,
            rerankEnabled = true,
            nBest = 4,
        ).prepareRerankPlan(
            input = "しかい",
            candidates = listOf(
                Candidate(string = "A", type = 1, length = 3u, score = 1),
                Candidate(string = "B", type = 1, length = 3u, score = 2),
            ),
            policy = com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest(
                input = "しかい",
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
            ).runtimeConversionPolicy,
        )
        requireNotNull(plan)
        coordinator.putCachedZenzRerank(plan.cacheKey, listOf(Candidate(string = "C", type = 1, length = 3u, score = 3)))
        assertEquals("C", coordinator.getCachedZenzRerank(plan.cacheKey)?.first()?.string)
    }

    private fun fakeConverter(candidates: List<Candidate>): KanaKanjiConverter {
        return object : KanaKanjiConverter {
            override suspend fun requestCandidates(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = ConvertCandidatesResponse(
                result = ConversionResult(mainResults = candidates),
                bunsetsuResult = null,
            )

            override suspend fun requestCandidatesPostProcessed(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                postProcess: CandidatePostProcessEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = requestCandidates(input, options, runtime, environment, mode)

            override suspend fun requestCandidatesPostProcessedWithZenzRerank(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                postProcess: CandidatePostProcessEnvironment,
                zenzRerank: ZenzRerankRequest?,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = requestCandidatesPostProcessed(
                input, options, runtime, environment, postProcess, mode,
            )

            override suspend fun requestPostCompositionPredictionCandidates(
                leftSideCandidate: Candidate,
                useLearnedTransitions: Boolean,
            ): List<PostCompositionPredictionCandidate> = emptyList()

            override fun requestEnglishKanaCandidates(input: ComposingText): List<Candidate> = emptyList()

            override fun stopComposition(sessionId: String, keepCompletedData: Boolean) = Unit

            override suspend fun experimentalRequestTypoCorrection(
                leftSideContext: String,
                composingText: ComposingText,
                options: ConvertRequestOptions,
                inputStyle: InputStyle,
                session: ConversionSession,
            ): List<com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidate> = emptyList()
        }
    }
    private fun testPreferences(): ImeCandidatePreferences {
        return ImeCandidatePreferences(
            nBest = 4,
            useUserDictionary = false,
            useUserTemplate = false,
            useRomajiCandidates = false,
            useBunsetsu = false,
            useOmissionSearch = false,
            learningType = AzooKeyStyleLearningType.Nothing,
            zenzaiMode = AzooKeyStyleZenzaiMode.Off,
            liveConversionMode = AzooKeyLiveConversionMode.Disabled,
            privacy = CandidateRequestPrivacy(),
            versionString = null,
            learnedPrefixMatchThreshold = 1,
            userDictionaryPrefixMatchThreshold = 1,
            isLearnDictionaryMode = false,
            romanize = { null },
            toHankakuAlphabet = { it },
            onNormalBunsetsuResult = { _, _ -> },
            isNgWordFilterEnabled = false,
            ngWords = emptyList(),
            ngWordPattern = Regex(""),
            isOrderOverrideEnabled = false,
        )
    }
}