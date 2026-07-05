package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * AzooKey [processResult](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の zenzai value reorder 相当。
 */
object AzooKeyZenzaiValueReorder {
    fun reorderTopValues(candidates: List<Candidate>): List<Candidate> {
        if (candidates.isEmpty()) return candidates
        val values = candidates.map { it.value }.sortedDescending()
        return candidates.mapIndexed { index, candidate ->
            candidate.copy(value = values[index])
        }
    }

    fun rerankByZenzScores(
        candidates: List<Candidate>,
        rawZenzScores: List<Float>,
    ): List<Candidate>? {
        if (candidates.isEmpty()) return emptyList()
        if (candidates.size != rawZenzScores.size || rawZenzScores.none { it.isFinite() }) {
            return null
        }
        val order = candidates.indices.sortedWith(
            compareByDescending<Int> { rawZenzScores[it] }.thenBy { it },
        )
        val values = candidates.map { it.value }.sortedDescending()
        return order.mapIndexed { rank, originalIndex ->
            candidates[originalIndex].copy(value = values[rank])
        }
    }
}
