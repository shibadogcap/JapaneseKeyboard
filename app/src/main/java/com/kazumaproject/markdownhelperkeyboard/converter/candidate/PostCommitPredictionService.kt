package com.kazumaproject.markdownhelperkeyboard.converter.candidate

class PostCommitPredictionService(
    private val learnedLimit: Int = 15,
    private val loudsTransitionLimit: Int = 15,
    private val zeroHintLimit: Int = 15,
    private val composerEmojiLimit: Int = 3,
    private val searchLearnedTransitions: suspend (committedText: String, limit: Int) -> List<Candidate>,
    private val searchLoudsTransitions: suspend (transitionReading: String, limit: Int) -> List<Candidate> = { _, _ ->
        emptyList()
    },
    private val searchZeroHintCandidates: suspend (leftSideCandidate: Candidate, limit: Int) -> List<Candidate> = { _, _ ->
        emptyList()
    },
    private val emojiProvider: PostCommitEmojiDictionaryProvider,
) {
    suspend fun predict(
        leftSideCandidate: Candidate,
        useLearnedTransitions: Boolean,
    ): List<Candidate> {
        val committedText = leftSideCandidate.string
        if (committedText.isBlank()) {
            return emptyList()
        }

        val learnedTransitions = if (useLearnedTransitions) {
            searchLearnedTransitions(committedText, learnedLimit)
        } else {
            emptyList()
        }
        val transitionReading = AzooKeyPostCommitPredictionPolicy.nextWordTransitionReading(leftSideCandidate)
        val loudsTransitions = if (transitionReading != null) {
            searchLoudsTransitions(transitionReading, loudsTransitionLimit)
        } else {
            emptyList()
        }
        val predictionTransitions = (learnedTransitions + loudsTransitions)
            .distinctBy { it.string }
            .sortedByDescending { it.value }
        val zeroHintCandidates = if (AzooKeyPostCommitPredictionPolicy.shouldSearchZeroHint(leftSideCandidate)) {
            searchZeroHintCandidates(leftSideCandidate, zeroHintLimit)
        } else {
            emptyList()
        }
        val emojiCandidates = emojiProvider.provide(
            committedText = committedText,
            committedReading = leftSideCandidate.yomi?.takeIf { it.isNotBlank() },
        )

        return PostCommitPredictionComposer.compose(
            committedText = committedText,
            learnedTransitions = predictionTransitions,
            emojiCandidates = emojiCandidates,
            zeroHintCandidates = zeroHintCandidates,
            emojiLimit = composerEmojiLimit,
        )
    }
}
