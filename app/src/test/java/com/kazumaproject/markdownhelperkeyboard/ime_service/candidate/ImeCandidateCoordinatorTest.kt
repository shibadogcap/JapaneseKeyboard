package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
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
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionService
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeCandidateCoordinatorTest {
    @Test
    fun suggestUsesKanaKanjiConverterApi() = runTest {
        val raw = listOf(Candidate(string = "司会", type = 1, length = 2u, score = 100))
        val filtered = listOf(raw.first().copy(score = 200))
        val fakeConverter = object : KanaKanjiConverter {
            override suspend fun requestCandidates(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse {
                assertEquals("しかい", input.convertTarget)
                return ConvertCandidatesResponse(
                    result = ConversionResult(mainResults = raw),
                    bunsetsuResult = null,
                )
            }

            override suspend fun requestCandidatesPostProcessed(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                postProcess: CandidatePostProcessEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse {
                assertTrue(postProcess.isOrderOverrideEnabled)
                return ConvertCandidatesResponse(
                    result = ConversionResult(mainResults = filtered),
                    bunsetsuResult = null,
                )
            }

            override suspend fun requestPostCompositionPredictionCandidates(
                leftSideCandidate: Candidate,
                useLearnedTransitions: Boolean,
            ): List<PostCompositionPredictionCandidate> = emptyList()

            override fun requestEnglishKanaCandidates(input: ComposingText): List<Candidate> =
                emptyList()

            override fun stopComposition(sessionId: String, keepCompletedData: Boolean) = Unit

            override suspend fun requestCandidatesPostProcessedWithZenzRerank(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                postProcess: CandidatePostProcessEnvironment,
                zenzRerank: com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest?,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = requestCandidatesPostProcessed(
                input, options, runtime, environment, postProcess, mode,
            )
        }
        val coordinator = ImeCandidateCoordinator(
            kanaKanjiConverter = fakeConverter,
            zenzConversionService = ZenzConversionService(
                zenzEngine = object : com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort {
                    override suspend fun generateWithContext(
                        profile: String,
                        leftContext: String,
                        inputKatakana: String,
                        maxTokens: Int,
                    ): String = ""

                    override suspend fun scoreCandidates(
                        profile: String,
                        leftContext: String,
                        inputKatakana: String,
                        candidates: List<String>,
                    ): FloatArray = FloatArray(candidates.size)

                    override suspend fun candidateEvaluate(
                        profile: String,
                        leftContext: String,
                        inputKatakana: String,
                        candidate: String,
                    ): String = ""
                },
            ),
        )

        val result = coordinator.suggest(
            input = "しかい",
            mode = CandidateRequestMode.Normal,
            preferences = testPreferences(),
        )

        assertEquals(filtered, result.candidates)
    }

    @Test
    fun predictPostCommitCandidatesUsesConverterApiAndPreservesCandidateType() = runTest {
        val fakeConverter = object : KanaKanjiConverter {
            override suspend fun requestCandidates(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = ConvertCandidatesResponse(
                ConversionResult(mainResults = emptyList()),
                null,
            )

            override suspend fun requestCandidatesPostProcessed(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                postProcess: CandidatePostProcessEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = ConvertCandidatesResponse(
                ConversionResult(mainResults = emptyList()),
                null,
            )

            override suspend fun requestCandidatesPostProcessedWithZenzRerank(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                postProcess: CandidatePostProcessEnvironment,
                zenzRerank: ZenzRerankRequest?,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = ConvertCandidatesResponse(
                ConversionResult(mainResults = emptyList()),
                null,
            )

            override suspend fun requestPostCompositionPredictionCandidates(
                leftSideCandidate: Candidate,
                useLearnedTransitions: Boolean,
            ): List<PostCompositionPredictionCandidate> {
                assertEquals("確認", leftSideCandidate.string)
                assertEquals(true, useLearnedTransitions)
                return listOf(
                    PostCompositionPredictionCandidate(
                        text = "できます",
                        value = 10f,
                        candidateType = CandidateType.NBEST,
                    )
                )
            }

            override fun requestEnglishKanaCandidates(input: ComposingText): List<Candidate> =
                emptyList()

            override fun stopComposition(sessionId: String, keepCompletedData: Boolean) = Unit
        }
        val coordinator = ImeCandidateCoordinator(
            kanaKanjiConverter = fakeConverter,
            zenzConversionService = ZenzConversionService(
                zenzEngine = object : com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort {
                    override suspend fun generateWithContext(
                        profile: String,
                        leftContext: String,
                        inputKatakana: String,
                        maxTokens: Int,
                    ): String = ""

                    override suspend fun scoreCandidates(
                        profile: String,
                        leftContext: String,
                        inputKatakana: String,
                        candidates: List<String>,
                    ): FloatArray = FloatArray(candidates.size)

                    override suspend fun candidateEvaluate(
                        profile: String,
                        leftContext: String,
                        inputKatakana: String,
                        candidate: String,
                    ): String = ""
                },
            ),
        )

        val result = coordinator.predictPostCommitCandidates(
            leftSideCandidate = Candidate(
                string = "確認",
                type = CandidateType.NBEST,
                length = 2u,
                score = 0,
            ),
            useLearnedTransitions = true,
        )

        assertEquals(listOf("できます"), result.map { it.string })
        assertEquals(CandidateType.NBEST, result.first().type)
    }

    private fun testPreferences(): ImeCandidatePreferences {
        return ImeCandidatePreferences(
            nBest = 4,
            useUserDictionary = true,
            useUserTemplate = false,
            useRomajiCandidates = false,
            useBunsetsu = false,
            useOmissionSearch = false,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
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
            isOrderOverrideEnabled = true,
        )
    }
}
