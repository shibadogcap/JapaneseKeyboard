package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionConfig

/**
 * IME セッションから渡す Zenz 実行コンテキスト。
 * [ImeCandidateCoordinator] が [CandidateService] 経路と同じ policy で Zenz を扱う。
 */
data class ImeCandidateZenzContext(
    val config: ZenzConversionConfig,
    val leftContext: String,
    val zenzEnabled: Boolean,
    val zenzaiEvaluationEnabled: Boolean,
    val asyncGenerationEnabled: Boolean,
    val rerankEnabled: Boolean,
    val nBest: Int,
) {
    /**
     * Legacy [IMEService.setCandidates] parity: emit before dictionary suggest when Zenz is on,
     * hardware keyboard is absent, and rerank is off (validation happens in performZenzRequest).
     */
    fun shouldEmitAsyncGeneration(
        @Suppress("UNUSED_PARAMETER") input: String,
        @Suppress("UNUSED_PARAMETER") policy: AzooKeyRuntimeConversionPolicy,
    ): Boolean {
        return asyncGenerationEnabled && zenzEnabled && !rerankEnabled
    }

    /**
     * Legacy parity: second emit path when rerank + Zenzai evaluation prefs are active.
     */
    fun shouldEmitAsyncZenzai(
        @Suppress("UNUSED_PARAMETER") input: String,
        @Suppress("UNUSED_PARAMETER") policy: AzooKeyRuntimeConversionPolicy,
    ): Boolean {
        return zenzaiEvaluationEnabled && zenzEnabled && rerankEnabled
    }

    fun canPrepareRerank(
        input: String,
        policy: AzooKeyRuntimeConversionPolicy,
        candidateCount: Int,
    ): Boolean {
        return rerankEnabled &&
            zenzEnabled &&
            !policy.shouldUseZenzai &&
            policy.allowsPersonalizedConversion &&
            !config.hasHardwareKeyboard &&
            input.length > 1 &&
            candidateCount >= 2
    }

    companion object {
        const val RERANK_TOP_K: Int = 4
        const val RERANK_ALPHA: Float = 0.7f
        const val RERANK_BETA: Float = 0.3f

        val LIVE_MIX_OPTIONS = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConvertRequestOptions(
            japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
        )
    }
}

data class ImeCandidateZenzRerankPlan(
    val leftContext: String,
    val cacheKey: String,
    val rerankTargets: List<IndexedValue<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>>,
)

fun ImeCandidateZenzContext.prepareRerankPlan(
    input: String,
    candidates: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>,
    policy: AzooKeyRuntimeConversionPolicy,
): ImeCandidateZenzRerankPlan? {
    if (!canPrepareRerank(input, policy, candidates.size)) return null
    val targets = candidates.withIndex()
        .filter { it.value.length.toInt() == input.length }
        .take(ImeCandidateZenzContext.RERANK_TOP_K)
    if (targets.size < 2) return null
    val cacheKey = buildString {
        append(config.profile)
        append('\u0001')
        append(leftContext)
        append('\u0001')
        append(input.hiraganaToKatakana())
        targets.forEach {
            append('\u0002')
            append(it.index)
            append('\u0003')
            append(it.value.string)
            append('\u0003')
            append(it.value.score)
        }
    }
    return ImeCandidateZenzRerankPlan(
        leftContext = leftContext,
        cacheKey = cacheKey,
        rerankTargets = targets,
    )
}

fun ImeCandidateZenzContext.toRerankRequest(
    input: String,
    candidates: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>,
    cursorPosition: Int? = null,
): com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest {
    return com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzRerankRequest(
        insertReading = input,
        candidates = candidates,
        leftContext = leftContext,
        config = config.copy(
            rerankEnabled = true,
            rerankTopK = ImeCandidateZenzContext.RERANK_TOP_K,
            rerankBaseWeight = ImeCandidateZenzContext.RERANK_ALPHA,
            rerankZenzWeight = ImeCandidateZenzContext.RERANK_BETA,
        ),
        cursorPosition = cursorPosition,
    )
}

fun CandidateRequest.runtimePolicy(): AzooKeyRuntimeConversionPolicy = runtimeConversionPolicy