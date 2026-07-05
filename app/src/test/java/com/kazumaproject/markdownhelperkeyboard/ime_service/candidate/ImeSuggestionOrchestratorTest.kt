package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
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
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionService
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiComposingSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImeSuggestionOrchestratorTest {
    private lateinit var coordinator: ImeCandidateCoordinator

    @Before
    fun setUp() {
        coordinator = createTestCoordinator()
    }

    @Test
    fun syncComposingSessionClearsSessionForEmptyDirectInput() {
        val orchestrator = ImeSuggestionOrchestrator(coordinator)
        coordinator.conversionSession.previousComposingText = ComposingText.fromConvertTarget("あ")
        coordinator.conversionSession.lastConvertTarget = "あ"
        coordinator.conversionSession.latticeIncrementalState.normalizedInput = "ア"

        orchestrator.syncComposingSession(
            displayInput = "あ",
            useQwertyRoman2Kana = false,
            roman2Kana = com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.Identity,
            romajiSnapshot = null,
            qwertyRomajiRawInput = null,
            zenkakuRomaji = false,
        )
        orchestrator.syncComposingSession(
            displayInput = "",
            useQwertyRoman2Kana = false,
            roman2Kana = com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.Identity,
            romajiSnapshot = null,
            qwertyRomajiRawInput = null,
            zenkakuRomaji = false,
        )
        assertFalse(coordinator.composingTextSession.isActive())
        org.junit.Assert.assertNull(coordinator.conversionSession.previousComposingText)
        org.junit.Assert.assertNull(coordinator.conversionSession.lastConvertTarget)
        org.junit.Assert.assertNull(coordinator.conversionSession.latticeIncrementalState.normalizedInput)
    }

    @Test
    fun requestSuggestionResultInvokesBunsetsuMergedOnce() = runTest {
        val orchestrator = ImeSuggestionOrchestrator(coordinator)
        var mergeCount = 0
        val result = orchestrator.requestSuggestionResult(
            insertString = "しかい",
            mode = CandidateRequestMode.Normal,
            preferences = testPreferences(),
            zenz = null,
            composingText = ComposingText.fromConvertTarget("しかい"),
            roman2Kana = com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.Identity,
            onBunsetsuMerged = { _, _, _ -> mergeCount += 1 },
        )
        assertEquals(1, mergeCount)
        assertFalse(result.emitAsyncZenzGeneration)
        assertFalse(result.emitAsyncZenzai)
    }

    @Test
    fun syncComposingSessionAppliesPhysicalKeyboardSnapshotWhenDisplayMatches() {
        val orchestrator = ImeSuggestionOrchestrator(coordinator)
        val roman2Kana = AzooKeyRoman2KanaTransducer.fromMap(
            mapOf("kai" to ("かい" to 3)),
        )
        val snapshot = RomajiComposingSnapshot(
            committedSurface = "か",
            pendingRomaji = "kai",
        )
        orchestrator.syncComposingSession(
            displayInput = snapshot.displayText,
            useQwertyRoman2Kana = true,
            roman2Kana = roman2Kana,
            romajiSnapshot = snapshot,
            qwertyRomajiRawInput = null,
            zenkakuRomaji = false,
        )
        assertTrue(coordinator.composingTextSession.isActive())
        assertEquals("かかい", coordinator.composingTextSession.current().convertTarget)
    }

    @Test
    fun composingTextForCandidateRequestSyncsQwertyRawBufferBeforeResolve() {
        val orchestrator = ImeSuggestionOrchestrator(coordinator)
        val roman2Kana = AzooKeyRoman2KanaTransducer.fromMap(
            mapOf("kai" to ("かい" to 3)),
        )
        orchestrator.composingTextForCandidateRequest(
            displayInput = "かい",
            useQwertyRoman2Kana = true,
            roman2Kana = roman2Kana,
            romajiSnapshot = null,
            qwertyRomajiRawInput = "kai",
            zenkakuRomaji = false,
        )
        assertTrue(coordinator.composingTextSession.isActive())
        assertEquals("かい", coordinator.composingTextSession.current().convertTarget)
    }

    @Test
    fun suggestionListEnglishKanaDelegatesToCoordinator() {
        val orchestrator = ImeSuggestionOrchestrator(createEnglishKanaCoordinator())
        val results = orchestrator.suggestionListEnglishKana("hello")
        assertEquals(listOf("ハロー"), results.map { it.string })
    }

    @Test
    fun suggestionListWithoutPredictionReturnsCandidatesWithZenzDisabled() = runTest {
        val orchestrator = ImeSuggestionOrchestrator(coordinator)
        val candidates = orchestrator.suggestionListWithoutPrediction(
            insertString = "しかい",
            preferences = testPreferences(),
            composingText = ComposingText.fromConvertTarget("しかい"),
            roman2Kana = com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.Identity,
            onBunsetsuMerged = { _, _, _ -> },
        )
        assertEquals(listOf("司会"), candidates.map { it.string })
    }

    private fun createEnglishKanaCoordinator(): ImeCandidateCoordinator {
        val fakeConverter = object : KanaKanjiConverter {
            override suspend fun requestCandidates(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = ConvertCandidatesResponse(
                ConversionResult(emptyList()),
                null,
            )

            override suspend fun requestCandidatesPostProcessed(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                postProcess: CandidatePostProcessEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse = requestCandidates(input, options, runtime, environment, mode)

            override suspend fun requestPostCompositionPredictionCandidates(
                leftSideCandidate: Candidate,
                useLearnedTransitions: Boolean,
            ): List<PostCompositionPredictionCandidate> = emptyList()

            override fun requestEnglishKanaCandidates(input: ComposingText): List<Candidate> {
                assertEquals("hello", input.convertTarget)
                return listOf(Candidate(string = "ハロー", type = 1, length = 3u, score = 1))
            }

            override fun stopComposition(sessionId: String, keepCompletedData: Boolean) = Unit

            override suspend fun experimentalRequestTypoCorrection(
                leftSideContext: String,
                composingText: ComposingText,
                options: ConvertRequestOptions,
                inputStyle: InputStyle,
                session: ConversionSession,
            ): List<com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidate> = emptyList()

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
        return ImeCandidateCoordinator(fakeConverter, zenzEnginePort())
    }

    private fun createTestCoordinator(): ImeCandidateCoordinator {
        val raw = listOf(Candidate(string = "司会", type = 1, length = 2u, score = 100))
        val fakeConverter = object : KanaKanjiConverter {
            override suspend fun requestCandidates(
                input: ComposingText,
                options: ConvertRequestOptions,
                runtime: ConvertRuntimeContext,
                environment: ImeCandidateEnvironment,
                mode: CandidateRequestMode,
            ): ConvertCandidatesResponse {
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
            ): ConvertCandidatesResponse = requestCandidates(input, options, runtime, environment, mode)

            override suspend fun requestPostCompositionPredictionCandidates(
                leftSideCandidate: Candidate,
                useLearnedTransitions: Boolean,
            ): List<PostCompositionPredictionCandidate> = emptyList()

            override fun requestEnglishKanaCandidates(input: ComposingText): List<Candidate> =
                emptyList()

            override fun stopComposition(sessionId: String, keepCompletedData: Boolean) = Unit

            override suspend fun experimentalRequestTypoCorrection(
                leftSideContext: String,
                composingText: ComposingText,
                options: ConvertRequestOptions,
                inputStyle: InputStyle,
                session: ConversionSession,
            ): List<com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidate> = emptyList()

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
        return ImeCandidateCoordinator(fakeConverter, zenzEnginePort())
    }

    private fun zenzEnginePort(): ZenzConversionService {
        return ZenzConversionService(
            zenzEngine = object : com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort {
                override suspend fun generateWithContext(
                    prompt: com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPromptContext,
                    inputKatakana: String,
                    maxTokens: Int,
                ): String = ""

                override suspend fun predictNextInputText(
                    prompt: com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPromptContext,
                    composingText: String,
                    count: Int,
                    minLength: Int,
                    maxEntropy: Float?,
                    possibleNexts: List<String>,
                ): String = ""

                override suspend fun typoEncodeRaw(text: String): IntArray = IntArray(0)

                override suspend fun typoNextLogProbs(
                    promptPrefix: String,
                    emittedTokenIds: IntArray,
                ): FloatArray? = null

                override fun typoTokenToSingleCharacter(tokenId: Int): Char? = null

                override fun vocabSize(): Int = 0

                override suspend fun scoreCandidates(
                    prompt: com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPromptContext,
                    inputKatakana: String,
                    candidates: List<String>,
                ): FloatArray = FloatArray(candidates.size)

                override suspend fun candidateEvaluate(
                    prompt: com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPromptContext,
                    inputKatakana: String,
                    candidate: String,
                    requestRichCandidates: Boolean,
                ): String = ""
            },
        )
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
            learnedPrefixMatchThreshold = 0,
            userDictionaryPrefixMatchThreshold = 0,
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