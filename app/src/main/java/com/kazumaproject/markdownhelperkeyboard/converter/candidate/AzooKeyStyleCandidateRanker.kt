package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Keeps candidate mixing rules out of the IME surface.
 *
 * azooKey's converter treats learning, prediction, special providers, and neural
 * conversion as request options. This ranker is the Android-side staging point
 * for that style: generated candidates can stay source-specific, then be merged
 * here with explicit policy.
 */
object AzooKeyStyleCandidateRanker {
    fun rank(candidates: List<Candidate>): List<Candidate> {
        val sorted = if (candidates.any { it.isLearnedHistoryCandidate() }) {
            candidates.sortedWith(
                compareByDescending<Candidate> { it.isLearnedHistoryCandidate() }
                    .thenByDescending { it.value }
            )
        } else {
            candidates.sortedByDescending { it.value }
        }
        return sorted.distinctBy { it.string }
    }

    private fun Candidate.isLearnedHistoryCandidate(): Boolean {
        return CandidateType.laneOf(this) == CandidateLane.Learned
    }
}
