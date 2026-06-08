package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRuntimeContext
import com.kazumaproject.markdownhelperkeyboard.converter.api.KanaKanjiConverter
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionService
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzGenerationRequest
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
        val options = ImeCandidateRequestFactory.buildConvertRequestOptions(preferences, mode)
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

        val request = com.kazumaproject.markdownhelperkeyboard.converter.api.CandidateRequestBridge
            .toCandidateRequest(composingText, options, runtime, mode)
        val policy = request.runtimeConversionPolicy

        val rerankPlan = zenz?.prepareRerankPlan(
            input = input,
            candidates = response.result.mainResults,
            policy = policy,
        )
        conversionSession.recordConversion(composingText, response.bunsetsuResult)

        val cachedReranked = rerankPlan?.let { getCachedZenzRerank(it.cacheKey) }
        val candidates = cachedReranked ?: response.result.mainResults

        return ImeCandidateSuggestResult(
            candidates = candidates,
            bunsetsuResult = response.bunsetsuResult,
            zenzRerankPlan = if (cachedReranked == null) rerankPlan else null,
            emitAsyncZenzGeneration = zenz?.shouldEmitAsyncGeneration(input, policy) == true,
            emitAsyncZenzai = zenz?.shouldEmitAsyncZenzai(input, policy) == true,
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
        val reranked = zenzConversionService.rerank(
            request = zenz.toRerankRequest(input, baseCandidates, cursorPosition).copy(
                leftContext = plan.leftContext,
            ),
            policy = request.runtimeConversionPolicy,
        ) ?: return null
        putCachedZenzRerank(plan.cacheKey, reranked)
        return reranked
    }

    fun prioritizeReranked(
        reranked: List<Candidate>,
    ): List<Candidate> {
        return com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleCandidateMixer.mix(
            mainCandidates = reranked,
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
                englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            ),
        ).mainResults
    }

    suspend fun generateLiveZenzCandidates(
        input: String,
        zenz: ImeCandidateZenzContext,
        preferences: ImeCandidatePreferences,
        mode: CandidateRequestMode = CandidateRequestMode.Normal,
        cursorPosition: Int? = null,
    ): List<ZenzCandidate> {
        val request = buildCandidateRequest(input, preferences, mode)
        return zenzConversionService.generatePredictive(
            request = ZenzGenerationRequest(
                insertReading = input,
                leftContext = zenz.leftContext,
                config = zenz.config,
                cursorPosition = cursorPosition,
            ),
            policy = request.runtimeConversionPolicy,
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
                dictionaryCandidates = dictionaryCandidates.map { it.string },
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
        return ImeCandidateLiveZenzMixer.mergeIfApplicable(
            insertReading = insertReading,
            dictionaryCandidates = dictionaryCandidates,
            zenzCandidates = zenzCandidates,
        )
    }

    fun committedCandidateForPostCommit(
        surface: String,
        tapped: Candidate? = null,
        fallbackReading: String? = null,
    ): Candidate {
        return conversionSession.recordCommit(
            surface = surface,
            tapped = tapped,
            fallbackReading = fallbackReading,
        )
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

    fun suggestEnglishKana(input: String): List<Candidate> {
        return kanaKanjiConverter.requestEnglishKanaCandidates(
            ImeCandidateRequestFactory.composingText(input),
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
            options = ImeCandidateRequestFactory.buildConvertRequestOptions(preferences, mode),
            runtime = ImeCandidateRequestFactory.buildRuntimeContext(preferences),
            mode = mode,
        )
    }
}
