package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest

/**
 * AzooKey [KanaKanjiConverter.requestCandidates](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当の
 * 変換 API。UI / IME はこの interface のみを呼ぶのが目標状態。
 */
interface KanaKanjiConverter {
    suspend fun requestCandidates(
        input: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        environment: ImeCandidateEnvironment,
        mode: CandidateRequestMode = CandidateRequestMode.Normal,
    ): ConvertCandidatesResponse

    suspend fun requestCandidatesPostProcessed(
        input: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        environment: ImeCandidateEnvironment,
        postProcess: CandidatePostProcessEnvironment,
        mode: CandidateRequestMode = CandidateRequestMode.Normal,
    ): ConvertCandidatesResponse

    suspend fun requestCandidatesPostProcessedWithZenzRerank(
        input: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        environment: ImeCandidateEnvironment,
        postProcess: CandidatePostProcessEnvironment,
        zenzRerank: ZenzRerankRequest?,
        mode: CandidateRequestMode = CandidateRequestMode.Normal,
    ): ConvertCandidatesResponse

    suspend fun requestPostCompositionPredictionCandidates(
        leftSideCandidate: Candidate,
        useLearnedTransitions: Boolean,
    ): List<PostCompositionPredictionCandidate>

    /** 英字→かな候補（本家の英かな経路）。変換 API からのみ呼ぶ。 */
    fun requestEnglishKanaCandidates(input: ComposingText): List<Candidate>

    /**
     * AzooKey [stopComposition](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
     * IME セッションリセット時に engine 内 lattice キャッシュを破棄する。
     */
    fun stopComposition(
        sessionId: String = ConversionSession.DEFAULT_SESSION_ID,
        keepCompletedData: Boolean = false,
    )

    /** Swift [KanaKanjiConverter.experimentalRequestTypoCorrection](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
    suspend fun experimentalRequestTypoCorrection(
        leftSideContext: String,
        composingText: ComposingText,
        options: ConvertRequestOptions,
        inputStyle: InputStyle,
        session: ConversionSession = ConversionSession(sessionId = ConversionSession.DEFAULT_SESSION_ID),
    ): List<com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidate>
}

data class ConvertCandidatesResponse(
    val result: ConversionResult,
    val bunsetsuResult: BunsetsuCandidateResult?,
    val usedAfterComplete: Boolean = false,
)