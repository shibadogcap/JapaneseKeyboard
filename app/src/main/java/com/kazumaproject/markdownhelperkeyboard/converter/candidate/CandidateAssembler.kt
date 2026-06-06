package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Assembles separated candidate sources into an azooKey-style conversion result.
 *
 * This is intentionally small for now: source fetching still lives in
 * IMEService, while source-aware assembly is pure Kotlin and testable.
 */
object CandidateAssembler {
    fun assemble(
        request: CandidateRequest,
        sources: CandidateSources,
    ): AzooKeyStyleConversionResult {
        val memoryCandidates = if (request.shouldReadMemoryDictionary) {
            sources.memory + sources.learned
        } else {
            emptyList()
        }
        val mainCandidates =
            sources.userTemplate +
                sources.userDictionary +
                sources.system +
                memoryCandidates +
                sources.romaji
        return AzooKeyStyleCandidateMixer.mix(
            mainCandidates = mainCandidates,
            // AzooKey: 予測 lane は systemPrediction のみ。memory は main / lattice で競合。
            japanesePredictionCandidates = sources.systemPrediction,
            englishPredictionCandidates = sources.english,
            specialCandidates = sources.special,
            firstClauseCandidates = sources.firstClause,
            wordCandidates = sources.word + mainCandidates.drop(AZOO_KEY_FULL_SENTENCE_VISIBLE_LIMIT),
            options = request.toAzooKeyStyleOptions(),
            input = request.input,
        )
    }

    private const val AZOO_KEY_FULL_SENTENCE_VISIBLE_LIMIT = 5
}
