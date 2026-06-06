package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class AzooKeyPostCommitPredictionPolicyInput(
    val committedText: String,
    val isPrivateMode: Boolean,
    val suppressSuggestions: Boolean,
    val isJapaneseMode: Boolean,
)

object AzooKeyPostCommitPredictionPolicy {
    private val terminalTexts = setOf("。", ".", "．")

    fun shouldRequestPrediction(input: AzooKeyPostCommitPredictionPolicyInput): Boolean {
        return input.committedText.isNotBlank() &&
            !input.isPrivateMode &&
            !input.suppressSuggestions &&
            input.isJapaneseMode &&
            input.committedText !in terminalTexts
    }

    /**
     * AzooKey [getPredictionCandidates] は LOUDS を **totalRuby**（次語 reading）で引く。
     * 確定候補に yomi があればそれを優先し、なければ surface を key にする。
     */
    fun nextWordTransitionReading(candidate: Candidate): String? {
        return candidate.yomi?.takeIf { it.isNotBlank() }
            ?: candidate.string.takeIf { it.isNotBlank() }
    }

    /** zero-hint は確定語の dicdata（yomi / reading）があるときのみ（本家 preparts + last.rcid 近似）。 */
    fun shouldSearchZeroHint(candidate: Candidate): Boolean {
        return candidate.yomi?.isNotBlank() == true
    }

    fun shouldUseLearnedTransitions(
        isLearnDictionaryMode: Boolean,
        predictionLearnSearchEnabled: Boolean,
    ): Boolean {
        return isLearnDictionaryMode && predictionLearnSearchEnabled
    }

    fun shouldUseLearnedTransitionsWithFallback(
        isLearnDictionaryMode: Boolean?,
        predictionLearnSearchEnabled: Boolean?,
    ): Boolean {
        return shouldUseLearnedTransitions(
            isLearnDictionaryMode = isLearnDictionaryMode == true,
            predictionLearnSearchEnabled = predictionLearnSearchEnabled == true,
        )
    }
}

data class AzooKeyTransitionLearningPolicyInput(
    val previousCommittedText: String?,
    val committedText: String,
    val isLearnDictionaryMode: Boolean,
    val isPrivateMode: Boolean,
    val candidate: Candidate?,
)

object AzooKeyTransitionLearningPolicy {
    fun shouldLearnTransition(input: AzooKeyTransitionLearningPolicyInput): Boolean {
        val previous = input.previousCommittedText
        return !previous.isNullOrBlank() &&
            input.committedText.isNotBlank() &&
            previous != input.committedText &&
            input.isLearnDictionaryMode &&
            !input.isPrivateMode &&
            (input.candidate?.isLearningTarget ?: true)
    }
}