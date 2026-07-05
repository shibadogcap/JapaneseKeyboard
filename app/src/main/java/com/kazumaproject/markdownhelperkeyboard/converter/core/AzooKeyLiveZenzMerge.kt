package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate

object AzooKeyLiveZenzMerge {
    private val readingVariantTypes = setOf(
        CandidateType.KATAKANA.toInt(),
        CandidateType.HIRAGANA.toInt(),
        CandidateType.NBEST.toInt(),
        CandidateType.HALF_WIDTH_KATAKANA_SPECIAL.toInt(),
    )

    fun mergeIfApplicable(
        insertReading: String,
        dictionaryCandidates: List<Candidate>,
        zenzCandidates: List<ZenzCandidate>,
    ): List<Candidate>? {
        if (zenzCandidates.isEmpty()) return null
        if (zenzCandidates.first().originalString != insertReading) return null
        if (dictionaryCandidates.isEmpty()) return null

        val inputLength = insertReading.length
        val zenzAsCandidates = zenzCandidates
            .filter { it.string.length <= inputLength }
            .map { zenz ->
                val dictionaryMatch = dictionaryCandidates.firstOrNull { it.string == zenz.string }
                if (dictionaryMatch != null) {
                    dictionaryMatch.copy(
                        type = zenz.type,
                        score = zenz.score,
                        value = zenz.score.toFloat(),
                    )
                } else {
                    Candidate(
                        string = zenz.string,
                        type = zenz.type,
                        length = zenz.length,
                        score = zenz.score,
                        value = zenz.score.toFloat(),
                        yomi = zenz.originalString,
                    )
                }
            }
        val merged = (zenzAsCandidates + dictionaryCandidates.filter {
            it.type.toInt() != CandidateType.LEARNED_HISTORY.toInt() && it.string.length <= inputLength
        }).sortedByDescending { it.value }
        val mergedTop = AzooKeyZenzaiValueReorder.reorderTopValues(merged.take(5))
        val readingVariants = dictionaryCandidates.filter { candidate ->
            candidate.type.toInt() in readingVariantTypes ||
                AzooKeyJapaneseConversionText.isPureHalfWidthKatakana(candidate.string)
        }
        val combined = mergedTop + readingVariants.filter { variant ->
            mergedTop.none { it.string == variant.string }
        }
        return AzooKeyJapaneseConversionText.filterDisplayedCandidates(combined)
    }
}
