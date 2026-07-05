package com.kazumaproject.markdownhelperkeyboard.converter.candidate

class CandidatePostProcessor(
    private val isNgWordEnabled: Boolean,
    private val ngWordPattern: Regex,
    private val isOrderOverrideEnabled: Boolean,
    private val applyOrderOverride: suspend (input: String, candidates: List<Candidate>) -> List<Candidate>,
) {
    suspend fun process(
        input: String,
        candidates: List<Candidate>,
    ): List<Candidate> {
        val filteredCandidates = candidates
            .filter { candidate ->
                !isNgWordEnabled || !ngWordPattern.containsMatchIn(candidate.string)
            }
            .distinctBy { it.string }

        return if (isOrderOverrideEnabled) {
            applyOrderOverride(input, filteredCandidates)
        } else {
            filteredCandidates
        }
    }
}
