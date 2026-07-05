package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCharIdMap
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsTrie
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickTypoCandidates

/**
 * AzooKey LOUDS 上の誤変換 prefix 探索（本家 [movingTowardPrefixSearch](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の surface 経路簡易版）。
 */
object AzooKeyLoudsTypoPrefixSearch {
    private const val ROOT_NODE_INDEX = 1

    data class TypoPrefixMatch(
        val katakanaReading: String,
        val penaltyUsed: Int,
    )

    fun search(
        trie: AzooKeyLoudsTrie,
        charIdMap: AzooKeyCharIdMap,
        reading: String,
        maxPenalty: Int = 2,
        maxLen: Int = 12,
        maxResults: Int = 64,
    ): List<TypoPrefixMatch> {
        val normalized = reading.hiraganaToKatakana()
        if (normalized.length <= 2) return emptyList()
        val bestPenaltyByReading = LinkedHashMap<String, Int>(128)
        val buffer = StringBuilder(maxLen)

        fun acceptAtEnd(penaltyUsed: Int) {
            if (buffer.isEmpty()) return
            val yomi = buffer.toString()
            val previous = bestPenaltyByReading[yomi]
            if (previous == null || penaltyUsed < previous) {
                bestPenaltyByReading[yomi] = penaltyUsed
            }
        }

        fun dfs(strIndex: Int, nodeIndex: Int, penaltyUsed: Int) {
            if (penaltyUsed > maxPenalty) return
            if (buffer.length > maxLen) return
            if (bestPenaltyByReading.size >= maxResults) return
            if (strIndex >= normalized.length) {
                acceptAtEnd(penaltyUsed)
                return
            }

            val ch = normalized[strIndex]
            for (candidate in FlickTypoCandidates.forCharacter(ch)) {
                val nextPenalty = penaltyUsed + candidate.penalty
                if (nextPenalty > maxPenalty) continue
                val charId = charIdMap.encode(candidate.ch.toString())?.firstOrNull() ?: continue
                val child = trie.searchCharNodeIndex(nodeIndex, charId) ?: continue
                buffer.append(candidate.ch)
                dfs(strIndex + 1, child, nextPenalty)
                buffer.setLength(buffer.length - 1)
            }
        }

        dfs(strIndex = 0, nodeIndex = ROOT_NODE_INDEX, penaltyUsed = 0)
        return bestPenaltyByReading.entries
            .filter { (_, penalty) -> penalty > 0 }
            .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
            .map { (yomi, penalty) -> TypoPrefixMatch(yomi.hiraganaToKatakana(), penalty) }
    }
}