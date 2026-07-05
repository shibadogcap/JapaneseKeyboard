package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLoudsTrieTypoSearcher
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLoudsTypoSearcher

interface AzooKeyLoudsDictionarySearcher {
    fun exactEntries(reading: String): List<AzooKeyDictionaryEntry>

    fun prefixEntries(
        reading: String,
        maxDepth: Int = Int.MAX_VALUE,
        maxCount: Int = Int.MAX_VALUE,
    ): List<AzooKeyDictionaryEntry>

    fun commonPrefixEntries(reading: String): List<AzooKeyDictionaryEntry>
}

class AzooKeyLoudsDictionaryLookup(
    private val identifier: String,
    private val charIdMap: AzooKeyCharIdMap,
    private val loudsTrie: AzooKeyLoudsTrie,
    private val shardLoader: AzooKeyDictionaryShardLoader,
    private val sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
    private val shardShift: Int = DefaultShardShift,
) : AzooKeyLoudsDictionarySearcher {
    override fun exactEntries(reading: String): List<AzooKeyDictionaryEntry> {
        val nodeIndex = loudsTrie.searchNodeIndex(
            charIdMap.encode(reading.hiraganaToKatakana()) ?: return emptyList(),
        )
            ?: return emptyList()
        return loadEntriesAtNodeIndices(listOf(nodeIndex))
    }

    fun prefixEntries(reading: String): List<AzooKeyDictionaryEntry> {
        return prefixEntries(
            reading = reading,
            maxDepth = Int.MAX_VALUE,
            maxCount = Int.MAX_VALUE,
        )
    }

    override fun prefixEntries(
        reading: String,
        maxDepth: Int,
        maxCount: Int,
    ): List<AzooKeyDictionaryEntry> {
        val nodeIndices = loudsTrie.prefixNodeIndices(
            charIds = charIdMap.encode(reading.hiraganaToKatakana()) ?: return emptyList(),
            maxDepth = maxDepth,
            maxCount = maxCount,
        )
        return loadEntriesAtNodeIndices(nodeIndices)
    }

    override fun commonPrefixEntries(reading: String): List<AzooKeyDictionaryEntry> {
        val nodeIndices = loudsTrie.commonPrefixNodeIndices(
            charIds = charIdMap.encode(reading.hiraganaToKatakana()) ?: return emptyList()
        )
        return loadEntriesAtNodeIndices(nodeIndices)
    }

    fun typoSearcher(): AzooKeyLoudsTypoSearcher =
        AzooKeyLoudsTrieTypoSearcher(loudsTrie, charIdMap)

    fun entriesAtNodeIndices(nodeIndices: Collection<Int>): List<AzooKeyDictionaryEntry> {
        return loadEntriesAtNodeIndices(nodeIndices.toList())
    }

    fun trieTypoSearcher(): AzooKeyLoudsTrieTypoSearcher =
        AzooKeyLoudsTrieTypoSearcher(loudsTrie, charIdMap)

    private fun loadEntriesAtNodeIndices(nodeIndices: List<Int>): List<AzooKeyDictionaryEntry> {
        if (nodeIndices.isEmpty()) return emptyList()
        return nodeIndices
            .groupBy { it shr shardShift }
            .toSortedMap()
            .flatMap { (shardIndex, indices) ->
                val localIndices = indices.map { it and localMask }.toSet()
                shardLoader.loadLoudstxt3ShardEntries(
                    identifier = identifier,
                    shardIndex = shardIndex,
                    sourceKind = sourceKind,
                    indices = localIndices,
                )
            }
    }

    companion object {
        const val DefaultShardShift: Int = 11
        val DefaultLocalMask: Int = (1 shl DefaultShardShift) - 1
    }

    private val localMask: Int = (1 shl shardShift) - 1
}
