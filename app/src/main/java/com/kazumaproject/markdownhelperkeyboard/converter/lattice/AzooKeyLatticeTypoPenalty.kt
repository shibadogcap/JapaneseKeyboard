package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue

/**
 * 誤変換ペナルティを辞書 entry value へ反映（本家 DicdataStore.penaltizedElementIfFeasible 簡易版）。
 */
object AzooKeyLatticeTypoPenalty {
    private const val DEFAULT_PENALTY_UNIT: Float = -8f

    fun adjustedEntryOrNull(
        entry: AzooKeyDictionaryEntry,
        penaltyUsed: Int,
        readingLength: Int,
    ): AzooKeyDictionaryEntry? {
        if (penaltyUsed <= 0) return entry
        val ratio = penaltyRatio(entry.leftId ?: 1285)
        val adjust = DEFAULT_PENALTY_UNIT * penaltyUsed * ratio
        val adjustedValue = entry.value + adjust
        if (AzooKeyLoudsBackedDicdataStore.shouldRemove(adjustedValue, readingLength)) {
            return null
        }
        return entry.copy(value = adjustedValue)
    }

    private fun penaltyRatio(leftId: Int): Float = when (leftId) {
        in 0..1318 -> 1f
        else -> 1f
    }
}