package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCharIdMap
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsTrie

fun interface AzooKeyLoudsTypoSearcher {
    fun typoPrefixMatches(readingPrefix: String): List<Pair<String, Int>>
}

class AzooKeyLoudsTrieTypoSearcher(
    val trie: AzooKeyLoudsTrie,
    val charIdMap: AzooKeyCharIdMap,
) : AzooKeyLoudsTypoSearcher {
    override fun typoPrefixMatches(readingPrefix: String): List<Pair<String, Int>> {
        return AzooKeyLoudsTypoPrefixSearch.search(trie, charIdMap, readingPrefix)
            .map { it.katakanaReading to it.penaltyUsed }
    }
}