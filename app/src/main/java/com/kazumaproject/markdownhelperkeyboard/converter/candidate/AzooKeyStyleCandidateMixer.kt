package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana

/**
 * Android-side equivalent of azooKey's ConversionResult mixing stage.
 *
 * Conversion, prediction, English prediction, and top-level special candidates
 * stay source-specific until this policy decides what belongs in the visible
 * candidate row.
 */
object AzooKeyStyleCandidateMixer {
    fun mix(
        mainCandidates: List<Candidate>,
        japanesePredictionCandidates: List<Candidate> = emptyList(),
        englishPredictionCandidates: List<Candidate> = emptyList(),
        specialCandidates: List<Candidate> = emptyList(),
        firstClauseCandidates: List<Candidate> = emptyList(),
        wordCandidates: List<Candidate> = emptyList(),
        options: AzooKeyStyleConvertRequestOptions = AzooKeyStyleConvertRequestOptions(),
        input: String? = null,
    ): AzooKeyStyleConversionResult {
        val separatedJapanesePredictions =
            if (options.japanesePredictionMode.isEnabled) japanesePredictionCandidates else emptyList()
        val separatedEnglishPredictions =
            if (options.englishPredictionMode.isEnabled) englishPredictionCandidates else emptyList()

        val seen = LinkedHashSet<String>()
        val visibleCandidates = buildList {
            addUnique(mainCandidates.take(FULL_SENTENCE_VISIBLE_LIMIT), seen)
            if (options.japanesePredictionMode.shouldMix) {
                addUnique(separatedJapanesePredictions.take(AUTO_MIX_JAPANESE_PREDICTION_LIMIT), seen)
            }
            if (options.englishPredictionMode.shouldMix) {
                addUnique(separatedEnglishPredictions.take(AUTO_MIX_ENGLISH_PREDICTION_LIMIT), seen)
            }
            addUnique(specialCandidates, seen)
            addUnique(firstClauseCandidates.take(FIRST_CLAUSE_VISIBLE_LIMIT), seen)
            addWordCandidates(wordCandidates, seen)
        }

        return AzooKeyStyleConversionResult(
            mainResults = promoteExactReadingCandidate(
                candidates = visibleCandidates,
                input = input,
            ),
            predictionResults = separatedJapanesePredictions,
            englishPredictionResults = separatedEnglishPredictions,
            firstClauseResults = firstClauseCandidates,
        )
    }

    private fun MutableList<Candidate>.addUnique(
        candidates: List<Candidate>,
        seen: MutableSet<String>,
    ) {
        candidates.forEach { candidate ->
            if (candidate.string.isNotEmpty() && seen.add(candidate.string)) {
                add(candidate)
            }
        }
    }

    private fun MutableList<Candidate>.addWordCandidates(
        candidates: List<Candidate>,
        seen: MutableSet<String>,
    ) {
        val sorted = candidates.sortedWith(
            compareByDescending<Candidate> { it.length.toInt() }
                .thenByDescending { it.value }
        )
        addUnique(sorted, seen)
    }

    private fun promoteExactReadingCandidate(
        candidates: List<Candidate>,
        input: String?,
    ): List<Candidate> {
        if (candidates.size <= EXACT_READING_TARGET_INDEX) {
            return candidates
        }
        val targetInput = input?.hiraganaToKatakana()
        val index = candidates.indexOfFirst { candidate ->
            !candidate.yomi.isNullOrEmpty() &&
            candidate.string == candidate.yomi &&
            (targetInput == null || candidate.yomi.hiraganaToKatakana() == targetInput)
        }
        if (index < 0 || index <= EXACT_READING_TARGET_INDEX) {
            return candidates
        }
        return candidates.toMutableList().also { list ->
            val candidate = list.removeAt(index)
            list.add(EXACT_READING_TARGET_INDEX, candidate)
        }
    }

    private const val FULL_SENTENCE_VISIBLE_LIMIT = 5
    private const val FIRST_CLAUSE_VISIBLE_LIMIT = 5
    private const val AUTO_MIX_JAPANESE_PREDICTION_LIMIT = 3
    private const val AUTO_MIX_ENGLISH_PREDICTION_LIMIT = 3
    private const val EXACT_READING_TARGET_INDEX = 2
}
