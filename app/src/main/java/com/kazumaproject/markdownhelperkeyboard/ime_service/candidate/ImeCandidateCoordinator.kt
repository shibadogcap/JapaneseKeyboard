package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRuntimeContext
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.api.KanaKanjiConverter
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyLiveZenzMerge
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyReadingVariantAugmenter
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.api.displayCandidates
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateLanePresentation
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionService
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzGenerationRequest
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPredictiveRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImeCandidateCoordinator @Inject constructor(
    private val kanaKanjiConverter: KanaKanjiConverter,
    private val zenzConversionService: ZenzConversionService,
) {
    val conversionSession: ConversionSession = ConversionSession()
    val composingTextSession: ImeComposingTextSession = ImeComposingTextSession()

    suspend fun suggest(
        input: String,
        mode: CandidateRequestMode,
        preferences: ImeCandidatePreferences,
        zenz: ImeCandidateZenzContext? = null,
        composingTextOverride: ComposingText? = null,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): ImeCandidateSuggestResult {
        val composingText = composingTextOverride
            ?: composingTextSession.current().takeIf {
                composingTextSession.isActive() && it.convertTarget == input
            }
            ?: ImeCandidateRequestFactory.composingText(input)
        conversionSession.liveComposingText = composingText
        val inputStyle = composingText.input.lastOrNull()?.inputStyle
        val options = ImeCandidateRequestFactory.buildConvertRequestOptions(
            preferences = preferences,
            mode = mode,
            inputStyle = inputStyle,
        ).copy(roman2KanaTransducer = roman2Kana)
        val previousInput = conversionSession.lastConvertTarget
        val previousComposingText = conversionSession.previousComposingText
        val previousLatticeNodes = conversionSession.latticeIncrementalState.latticeNodes
            .takeIf { it.isNotEmpty() }
        val completedCandidate = conversionSession.completedCandidate
        val runtime = ImeCandidateRequestFactory.buildRuntimeContext(
            preferences = preferences,
            previousInput = previousInput,
            previousComposingText = previousComposingText,
            previousLatticeNodes = previousLatticeNodes,
            completedCandidate = completedCandidate,
            composingText = composingText,
        )
        val environment = ImeCandidateRequestFactory.buildEnvironment(
            preferences = preferences,
            conversionSession = conversionSession,
        )
        val postProcess = ImeCandidateRequestFactory.buildPostProcessEnvironment(
            preferences = preferences,
            hasNgWords = preferences.ngWords.isNotEmpty(),
        )

        val response = kanaKanjiConverter.requestCandidatesPostProcessed(
            input = composingText,
            options = options,
            runtime = runtime,
            environment = environment,
            postProcess = postProcess,
            mode = mode,
        )

        val request = com.kazumaproject.markdownhelperkeyboard.converter.api.CandidateRequestBridge
            .toCandidateRequest(composingText, options, runtime, mode)
        val policy = request.runtimeConversionPolicy

        val rerankPlan = if (options.zenzaiMode.isEnabled) {
            null
        } else {
            zenz?.prepareRerankPlan(
                input = input,
                candidates = response.result.mainResults,
                policy = policy,
            )
        }
        conversionSession.recordConversion(
            composingText,
            response.bunsetsuResult,
            conversionSession.latticeIncrementalState.latticeNodes.takeIf { it.isNotEmpty() },
        )
        if (response.usedAfterComplete) {
            conversionSession.consumeCompletedData()
        }

        val cachedReranked = rerankPlan?.let { getCachedZenzRerank(it.cacheKey) }
        val mainResults = cachedReranked ?: response.result.mainResults
        val filteredSupplementary = response.result.supplementaryCandidates
        val displayCandidates = if (cachedReranked != null) {
            CandidateLanePresentation.mergeForDisplay(mainResults, filteredSupplementary)
        } else {
            response.result.copy(
                mainResults = mainResults,
                supplementaryCandidates = filteredSupplementary,
            ).displayCandidates()
        }
        val augmentedDisplay = AzooKeyReadingVariantAugmenter.augment(
            candidates = displayCandidates,
            reading = input,
            includeHalfWidthKana = options.halfWidthKanaCandidate,
        )

        return ImeCandidateSuggestResult(
            candidates = augmentedDisplay,
            mainResults = mainResults,
            supplementaryCandidates = filteredSupplementary,
            predictionResults = response.result.predictionResults,
            englishPredictionResults = response.result.englishPredictionResults,
            bunsetsuResult = response.bunsetsuResult,
            zenzRerankPlan = if (cachedReranked == null) rerankPlan else null,
            emitAsyncZenzGeneration = zenz?.shouldEmitAsyncGeneration(input, policy) == true,
            emitAsyncZenzai = zenz?.shouldEmitAsyncZenzai(input, policy) == true,
            firstClauseResults = response.result.firstClauseResults,
        )
    }

    suspend fun rerankWithZenz(
        input: String,
        baseCandidates: List<Candidate>,
        plan: ImeCandidateZenzRerankPlan,
        zenz: ImeCandidateZenzContext,
        preferences: ImeCandidatePreferences,
        mode: CandidateRequestMode = CandidateRequestMode.Normal,
        cursorPosition: Int? = null,
    ): List<Candidate>? {
        val request = buildCandidateRequest(input, preferences, mode)
        val convertOptions = ImeCandidateRequestFactory.buildConvertRequestOptions(
            preferences = preferences,
            mode = mode,
        )
        val reranked = zenzConversionService.rerank(
            request = zenz.toRerankRequest(input, baseCandidates, cursorPosition).copy(
                leftContext = plan.leftContext,
            ),
            policy = request.runtimeConversionPolicy,
        ) ?: return null
        putCachedZenzRerank(plan.cacheKey, reranked)
        return AzooKeyReadingVariantAugmenter.augment(
            candidates = reranked,
            reading = input,
            includeHalfWidthKana = convertOptions.halfWidthKanaCandidate,
        )
    }

    fun prioritizeReranked(
        reranked: List<Candidate>,
    ): List<Candidate> {
        // Zenz rerank は辞書候補の順序を直接更新する。Mixer で再ソートするとスコア順に戻ってしまう。
        return reranked
    }

    suspend fun generateLiveZenzCandidates(
        input: String,
        zenz: ImeCandidateZenzContext,
        preferences: ImeCandidatePreferences,
        mode: CandidateRequestMode = CandidateRequestMode.Normal,
        cursorPosition: Int? = null,
    ): List<ZenzCandidate> {
        val baseRequest = buildCandidateRequest(input, preferences, mode)
        val predictedReading = zenzConversionService.getPredictiveReading(
            request = ZenzGenerationRequest(
                insertReading = input,
                leftContext = zenz.leftContext,
                config = zenz.config,
                cursorPosition = cursorPosition,
            ),
            policy = baseRequest.runtimeConversionPolicy,
        )
        if (predictedReading.isNullOrEmpty()) return emptyList()

        val combinedReading = input + predictedReading
        val composingText = ImeCandidateRequestFactory.composingText(combinedReading)
        val options = ImeCandidateRequestFactory.buildConvertRequestOptions(
            preferences = preferences,
            mode = mode,
            inputStyle = composingText.input.lastOrNull()?.inputStyle,
        )
        val runtime = ImeCandidateRequestFactory.buildRuntimeContext(preferences)
        val environment = ImeCandidateRequestFactory.buildEnvironment(
            preferences = preferences,
            conversionSession = conversionSession,
        )
        val postProcess = ImeCandidateRequestFactory.buildPostProcessEnvironment(
            preferences = preferences,
            hasNgWords = preferences.ngWords.isNotEmpty(),
        )

        val response = kanaKanjiConverter.requestCandidatesPostProcessed(
            input = composingText,
            options = options,
            runtime = runtime,
            environment = environment,
            postProcess = postProcess,
            mode = mode,
        )

        val bestMatch = response.result.mainResults.firstOrNull() ?: return emptyList()
        return listOf(
            ZenzCandidate(
                string = bestMatch.string,
                type = CandidateType.ZENZ,
                length = combinedReading.length.toUByte(),
                score = 1500,
                originalString = input,
            )
        )
    }

    suspend fun evaluateLiveZenzai(
        input: String,
        dictionaryCandidates: List<Candidate>,
        zenz: ImeCandidateZenzContext,
        preferences: ImeCandidatePreferences,
        mode: CandidateRequestMode = CandidateRequestMode.Normal,
        cursorPosition: Int? = null,
    ): List<ZenzCandidate> {
        if (dictionaryCandidates.isEmpty()) return emptyList()
        val request = buildCandidateRequest(input, preferences, mode)
        return zenzConversionService.evaluateZenzai(
            request = ZenzPredictiveRequest(
                insertReading = input,
                dictionaryCandidates = dictionaryCandidates,
                leftContext = zenz.leftContext,
                nBest = zenz.nBest,
                config = zenz.config,
                cursorPosition = cursorPosition,
            ),
        )
    }

    fun mergeLiveZenzCandidates(
        insertReading: String,
        dictionaryCandidates: List<Candidate>,
        zenzCandidates: List<ZenzCandidate>,
    ): List<Candidate>? {
        return AzooKeyLiveZenzMerge.mergeIfApplicable(
            insertReading = insertReading,
            dictionaryCandidates = dictionaryCandidates,
            zenzCandidates = zenzCandidates,
        )
    }

    fun setCompletedData(candidate: Candidate) {
        conversionSession.setCompletedData(candidate)
    }

    fun committedCandidateForPostCommit(
        surface: String,
        tapped: Candidate? = null,
        fallbackReading: String? = null,
    ): Candidate {
        val committed = conversionSession.recordCommit(
            surface = surface,
            tapped = tapped,
            fallbackReading = fallbackReading,
        )
        kanaKanjiConverter.stopComposition(
            sessionId = conversionSession.sessionId,
            keepCompletedData = true,
        )
        return committed
    }

    suspend fun predictPostCommitCandidates(
        leftSideCandidate: Candidate,
        useLearnedTransitions: Boolean,
    ): List<Candidate> {
        return kanaKanjiConverter.requestPostCompositionPredictionCandidates(
            leftSideCandidate = leftSideCandidate,
            useLearnedTransitions = useLearnedTransitions,
        ).map { it.toCandidate() }
    }

    fun resetConversionSession() {
        conversionSession.reset()
        kanaKanjiConverter.stopComposition(conversionSession.sessionId, keepCompletedData = false)
        composingTextSession.reset()
    }

    fun getCachedZenzRerank(cacheKey: String): List<Candidate>? {
        return conversionSession.getZenzRerank(cacheKey)
    }

    fun putCachedZenzRerank(cacheKey: String, candidates: List<Candidate>) {
        conversionSession.putZenzRerank(cacheKey, candidates)
    }

    fun clearZenzRerankCache() {
        conversionSession.clearZenzRerankCache()
    }

    fun getLeftSideContext(): String {
        return conversionSession.leftSideContext
    }

    fun updateLeftSideContext(context: String) {
        conversionSession.leftSideContext = context
    }

    fun suggestEnglishKana(input: String): List<Candidate> {
        return kanaKanjiConverter.requestEnglishKanaCandidates(
            ImeCandidateRequestFactory.composingText(input),
        )
    }

    /**
     * Swift [KanaKanjiConverter.experimentalRequestTypoCorrection](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
     * convertToLattice とは分離した experimental API。
     */
    suspend fun requestExperimentalTypoCorrection(
        composingText: ComposingText,
        preferences: ImeCandidatePreferences,
        inputStyle: InputStyle,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): List<AzooKeyZenzaiTypoCandidate> {
        if (!preferences.zenzaiMode.isEnabled) return emptyList()
        val options = ImeCandidateRequestFactory.buildConvertRequestOptions(
            preferences = preferences,
            mode = CandidateRequestMode.Normal,
            inputStyle = inputStyle,
        ).copy(roman2KanaTransducer = roman2Kana)
        return kanaKanjiConverter.experimentalRequestTypoCorrection(
            leftSideContext = preferences.zenzLeftSideContext,
            composingText = composingText,
            options = options,
            inputStyle = inputStyle,
            session = conversionSession,
        )
    }

    private fun buildCandidateRequest(
        input: String,
        preferences: ImeCandidatePreferences,
        mode: CandidateRequestMode,
        composingText: ComposingText? = null,
    ): com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest {
        return com.kazumaproject.markdownhelperkeyboard.converter.api.CandidateRequestBridge.toCandidateRequest(
            composingText = composingText ?: ImeCandidateRequestFactory.composingText(input),
            options = ImeCandidateRequestFactory.buildConvertRequestOptions(
                preferences = preferences,
                mode = mode,
                inputStyle = composingText?.input?.lastOrNull()?.inputStyle,
            ),
            runtime = ImeCandidateRequestFactory.buildRuntimeContext(preferences),
            mode = mode,
        )
    }
}
