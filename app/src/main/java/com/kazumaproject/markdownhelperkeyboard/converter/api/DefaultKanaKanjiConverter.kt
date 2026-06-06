package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateService
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionService
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.PostCommitPredictionFacade
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultKanaKanjiConverter @Inject constructor(
    private val candidateService: CandidateService,
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
    ): ConvertCandidatesResponse {
        if (input.isEmpty) {
            return ConvertCandidatesResponse(
                result = ConversionResult(mainResults = emptyList()),
                bunsetsuResult = null,
            )
        }
        val request = CandidateRequestBridge.toCandidateRequest(
            composingText = input,
            options = options,
            runtime = runtime,
            mode = mode,
        )
        val serviceResult = candidateService.convert(request, environment)
        return ConvertCandidatesResponse(
            result = serviceResult.conversionResult,
            bunsetsuResult = serviceResult.bunsetsuResult,
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
        val raw = requestCandidates(
            input = input,
            options = options,
            runtime = runtime,
            environment = environment,
            mode = mode,
        )
        val processed = candidateService.postProcess(
            input = input.convertTarget,
            candidates = raw.result.mainResults,
            environment = postProcess,
        )
        return raw.copy(
            result = raw.result.copy(mainResults = processed),
        )
    }

    override suspend fun requestCandidatesPostProcessedWithZenzRerank(
        input: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        environment: ImeCandidateEnvironment,
        postProcess: CandidatePostProcessEnvironment,
        zenzRerank: ZenzRerankRequest?,
        mode: CandidateRequestMode,
    ): ConvertCandidatesResponse {
        val response = requestCandidatesPostProcessed(
            input = input,
            options = options,
            runtime = runtime,
            environment = environment,
            postProcess = postProcess,
            mode = mode,
        )
        val rerank = zenzRerank ?: return response
        val request = CandidateRequestBridge.toCandidateRequest(
            composingText = input,
            options = options,
            runtime = runtime,
            mode = mode,
        )
        if (!zenzConversionService.shouldRerank(request, rerank.config)) {
            return response
        }
        val reranked = zenzConversionService.rerank(
            request = rerank,
            policy = request.runtimeConversionPolicy,
        ) ?: return response
        return response.copy(
            result = response.result.copy(mainResults = reranked),
        )
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
        return kanaKanjiEngine.getCandidatesEnglishKana(input = input.convertTarget)
            .distinctBy { it.string }
    }
}