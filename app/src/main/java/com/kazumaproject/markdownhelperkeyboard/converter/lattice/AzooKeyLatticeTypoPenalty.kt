package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue

/**
 * 誤変換ペナルティを辞書 entry value へ反映（本家 DicdataStore.penaltizedElementIfFeasible 相当）。
 */
object AzooKeyLatticeTypoPenalty {

    fun adjustedEntryOrNull(
        entry: AzooKeyDictionaryEntry,
        penaltyUsed: Int,
        wordLength: Int,
    ): AzooKeyDictionaryEntry? {
        if (penaltyUsed <= 0) return entry
        val ratio = penaltyRatio(entry.leftId ?: 1285)
        // 本家の getPenalty(data) / 2 ＝ (-2.0 / word.count) / 2 ＝ -1.0 / wordLength
        val length = wordLength.coerceAtLeast(1)
        val pUnit = -1f / length.toFloat()
        val adjust = pUnit * penaltyUsed.toFloat() * ratio
        val adjustedValue = entry.value + adjust
        if (AzooKeyLoudsBackedDicdataStore.shouldRemove(adjustedValue, length)) {
            return null
        }
        return entry.copy(value = adjustedValue)
    }

    private fun penaltyRatio(leftId: Int): Float {
        // 助詞147...368, 助動詞369...554
        return if (leftId in 147..554) {
            2.5f
        } else {
            1.0f
        }
    }
}