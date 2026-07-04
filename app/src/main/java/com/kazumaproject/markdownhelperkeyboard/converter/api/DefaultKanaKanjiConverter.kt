package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyKanaKanjiConverterEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionService
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.PostCommitPredictionFacade
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLearningMemoryRepository
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessor
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultKanaKanjiConverter @Inject constructor(
    private val converterEngine: AzooKeyKanaKanjiConverterEngine,
    private val learningMemoryRepository: AzooKeyLearningMemoryRepository,
    private val userTemplateRepository: UserTemplateRepository,
    private val candidateOrderOverrideRepository: CandidateOrderOverrideRepository,
    private val postCommitPredictionFacade: PostCommitPredictionFacade,
    private val kanaKanjiEngine: KanaKanjiEngine,
    private val zenzConversionService: ZenzConversionService,
) : KanaKanjiConverter {

    override suspend fun requestCandidates(
        input: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        environment: ImeCandidateEnvironment,
        mode: CandidateRequestMode,
    ): ConvertCandidatesResponse = withContext(Dispatchers.Default) {
        syncSessionState(runtime, environment)
        val engineResult = converterEngine.requestCandidates(
            inputData = input,
            options = applyMode(options, mode),
            session = environment.conversionSession ?: ConversionSession(),
            searchMemory = { reading, limit ->
                withContext(Dispatchers.IO) {
                    if (options.learningType != AzooKeyStyleLearningType.Nothing) {
                        learningMemoryRepository.prefixSearch(reading, limit.coerceAtMost(options.maxMemoryCount))
                    } else {
                        emptyList()
                    }
                }
            },
            searchUserTemplate = { query, limit ->
                withContext(Dispatchers.IO) {
                    if (!options.useUserTemplate) return@withContext emptyList()
                    userTemplateRepository.searchByReading(reading = query, limit = limit).map {
                        Candidate(
                            string = it.word,
                            type = CandidateType.USER_DICTIONARY,
                            length = it.word.length.toUByte(),
                            score = it.posScore,
                            value = it.posScore.toFloat(),
                            yomi = it.reading,
                            isLearningTarget = false,
                        )
                    }
                }
            },
        )
        val split = splitConversionResult(engineResult.conversionResult)
        ConvertCandidatesResponse(
            result = split,
            bunsetsuResult = engineResult.bunsetsuResult,
            usedAfterComplete = engineResult.usedAfterComplete,
        )
    }

    override fun stopComposition(sessionId: String, keepCompletedData: Boolean) {
        converterEngine.stopComposition(sessionId, keepCompletedData)
    }

    private fun splitConversionResult(
        raw: com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConversionResult,
    ): com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConversionResult {
        val (main, extracted) = com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateLanePresentation
            .splitMainAndSupplementary(raw.mainResults)
        return raw.copy(
            mainResults = main,
            supplementaryCandidates = raw.supplementaryCandidates + extracted,
        )
    }

    override suspend fun requestCandidatesPostProcessed(
        input: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        environment: ImeCandidateEnvironment,
        postProcess: CandidatePostProcessEnvironment,
        mode: CandidateRequestMode,
    ): ConvertCandidatesResponse = withContext(Dispatchers.Default) {
        val raw = requestCandidates(input, options, runtime, environment, mode)
        val processed = postProcessCandidates(input.convertTarget, raw.result.mainResults, postProcess)
        raw.copy(result = raw.result.copy(mainResults = processed))
    }

    override suspend fun requestCandidatesPostProcessedWithZenzRerank(
        input: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        environment: ImeCandidateEnvironment,
        postProcess: CandidatePostProcessEnvironment,
        zenzRerank: ZenzRerankRequest?,
        mode: CandidateRequestMode,
    ): ConvertCandidatesResponse = withContext(Dispatchers.Default) {
        val response = requestCandidatesPostProcessed(
            input = input,
            options = options,
            runtime = runtime,
            environment = environment,
            postProcess = postProcess,
            mode = mode,
        )
        val rerank = zenzRerank ?: return@withContext response
        val request = CandidateRequestBridge.toCandidateRequest(input, options, runtime, mode)
        if (!zenzConversionService.shouldRerank(request, rerank.config)) {
            return@withContext response
        }
        val reranked = zenzConversionService.rerank(request = rerank, policy = request.runtimeConversionPolicy)
            ?: return@withContext response
        response.copy(result = response.result.copy(mainResults = reranked))
    }

    override suspend fun requestPostCompositionPredictionCandidates(
        leftSideCandidate: Candidate,
        useLearnedTransitions: Boolean,
    ): List<PostCompositionPredictionCandidate> {
        return postCommitPredictionFacade.predict(
            leftSideCandidate = leftSideCandidate,
            useLearnedTransitions = useLearnedTransitions,
        ).map(PostCompositionPredictionCandidate::fromCandidate)
    }

    override fun requestEnglishKanaCandidates(input: ComposingText): List<Candidate> {
        if (input.isEmpty) return emptyList()
        return kanaKanjiEngine.getCandidatesEnglishKana(input = input.convertTarget).distinctBy { it.string }
    }

    private fun syncSessionState(runtime: ConvertRuntimeContext, environment: ImeCandidateEnvironment) {
        val session = environment.conversionSession ?: return
        runtime.completedCandidate?.let { converterEngine.setCompletedData(session.sessionId, it) }
    }

    private fun applyMode(options: ConvertRequestOptions, mode: CandidateRequestMode): ConvertRequestOptions {
        if (mode == CandidateRequestMode.WithoutPrediction || mode == CandidateRequestMode.EnglishKana) {
            return options.copy(
                requireJapanesePrediction = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode.Disabled,
            )
        }
        return options
    }

    private suspend fun postProcessCandidates(
        input: String,
        candidates: List<Candidate>,
        environment: CandidatePostProcessEnvironment,
    ): List<Candidate> {
        return CandidatePostProcessor(
            isNgWordEnabled = environment.isNgWordEnabled,
            ngWordPattern = environment.ngWordPattern,
            isOrderOverrideEnabled = false,
            applyOrderOverride = { orderInput, orderCandidates ->
                withContext(Dispatchers.IO) {
                    candidateOrderOverrideRepository.applyOrder(orderInput, orderCandidates)
                }
            },
        ).process(input, candidates)
    }
}
