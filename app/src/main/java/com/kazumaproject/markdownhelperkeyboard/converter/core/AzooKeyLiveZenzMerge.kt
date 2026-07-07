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
        val zenzAsCandidates = zenzCandidates.mapNotNull { zenz ->
            dictionaryCandidates.firstOrNull { it.string == zenz.string }?.let { dictionaryMatch ->
                dictionaryMatch.copy(
                    type = zenz.type,
                    score = zenz.score,
                    value = zenz.score.toFloat(),
                )
            }
        }
        if (zenzAsCandidates.isEmpty()) return null

        val merged = (zenzAsCandidates + dictionaryCandidates.filter {
            it.type.toInt() != CandidateType.LEARNED_HISTORY.toInt() && it.string.length <= inputLength
        }).sortedByDescending { it.value }
        val mergedTop = AzooKeyZenzaiValueReorder.reorderTopValues(merged.take(5))
        val readingVariants = dictionaryCandidates.filter { candidate ->
            candidate.type.toInt() in readingVariantTypes
        }
        return mergedTop + readingVariants.filter { variant ->
            mergedTop.none { it.string == variant.string }
        }
    }
}
