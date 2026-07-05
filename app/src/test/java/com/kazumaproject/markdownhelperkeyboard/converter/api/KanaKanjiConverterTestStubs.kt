package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest

fun KanaKanjiConverterStub(
    requestCandidatesImpl: suspend (
        ComposingText,
        ConvertRequestOptions,
        ConvertRuntimeContext,
        ImeCandidateEnvironment,
        CandidateRequestMode,
    ) -> ConvertCandidatesResponse,
): KanaKanjiConverter = object : KanaKanjiConverter {
    override suspend fun requestCandidates(
        input: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        environment: ImeCandidateEnvironment,
        mode: CandidateRequestMode,
    ): ConvertCandidatesResponse = requestCandidatesImpl(input, options, runtime, environment, mode)

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
    ): ConvertCandidatesResponse = requestCandidatesPostProcessed(input, options, runtime, environment, postProcess, mode)

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
    ): List<AzooKeyZenzaiTypoCandidate> = emptyList()
}
