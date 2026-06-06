package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionarySearcher

/**
 * AzooKey [DicdataStore.lookupDicdata](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当の辞書 lookup。
 */
interface AzooKeyDicdataStore {
    suspend fun buildLatticeNodes(
        input: String,
        useMemory: Boolean,
    ): List<AzooKeyLatticeNode>

    suspend fun buildLatticeNodesIncremental(
        input: String,
        useMemory: Boolean,
        previousNormalizedInput: String,
        previousNodes: List<AzooKeyLatticeNode>,
    ): List<AzooKeyLatticeNode>
}

class AzooKeyLoudsBackedDicdataStore(
    private val loudsLookups: List<AzooKeyLoudsDictionarySearcher>,
    private val searchMemory: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
    private val typoSearchers: List<AzooKeyLoudsTypoSearcher> = emptyList(),
    private val enableTypoCorrection: Boolean = false,
    private val maxWordLength: Int = DEFAULT_MAX_WORD_LENGTH,
    private val maxPrefixDepth: Int = DEFAULT_MAX_PREFIX_DEPTH,
    private val maxEntriesPerStart: Int = DEFAULT_MAX_ENTRIES_PER_START,
    private val valueThreshold: Float = DEFAULT_VALUE_THRESHOLD,
) : AzooKeyDicdataStore {
    override suspend fun buildLatticeNodes(
        input: String,
        useMemory: Boolean,
    ): List<AzooKeyLatticeNode> {
        val normalized = input.hiraganaToKatakana()
        if (normalized.isEmpty()) return emptyList()
        return buildLatticeNodesForStarts(
            normalized = normalized,
            useMemory = useMemory,
            startIndices = normalized.indices,
        )
    }

    override suspend fun buildLatticeNodesIncremental(
        input: String,
        useMemory: Boolean,
        previousNormalizedInput: String,
        previousNodes: List<AzooKeyLatticeNode>,
    ): List<AzooKeyLatticeNode> {
        val normalized = input.hiraganaToKatakana()
        val previous = previousNormalizedInput.hiraganaToKatakana()
        if (
            normalized.isEmpty() ||
            !normalized.startsWith(previous) ||
            normalized.length <= previous.length
        ) {
            return buildLatticeNodes(input, useMemory)
        }
        val previousLength = previous.length
        val reused = previousNodes.filter { it.endIndex <= previousLength }
        // 末尾追加でも start=0 付近の語が伸びる（例: シカ → シカイ で「司会」）ため 0..prevLen を再 lookup する。
        val refreshStarts = (0..previousLength) + (previousLength until normalized.length)
        val additional = buildLatticeNodesForStarts(
            normalized = normalized,
            useMemory = useMemory,
            startIndices = refreshStarts.distinct().sorted(),
        )
        return dedupeLatticeNodes(reused + additional)
    }

    private suspend fun buildLatticeNodesForStarts(
        normalized: String,
        useMemory: Boolean,
        startIndices: Iterable<Int>,
    ): List<AzooKeyLatticeNode> {
        val nodes = mutableListOf<AzooKeyLatticeNode>()
        for (start in startIndices) {
            val remaining = normalized.length - start
            val maxDepth = minOf(maxPrefixDepth, maxWordLength, remaining)
            if (maxDepth <= 0) continue
            val prefix = normalized.substring(start)

            val entries = linkedMapOf<Pair<String, String>, AzooKeyDictionaryEntry>()
            loudsLookups.forEach { lookup ->
                lookup.exactEntries(prefix).forEach { entry ->
                    if (entry.reading.length <= maxDepth) {
                        entries[entry.reading to entry.surface] = entry
                    }
                }
                lookup.commonPrefixEntries(prefix).forEach { entry ->
                    if (entry.reading.length <= maxDepth) {
                        entries[entry.reading to entry.surface] = entry
                    }
                }
            }
            if (useMemory) {
                searchMemory(prefix, maxEntriesPerStart).forEach { entry ->
                    if (entry.reading.length <= maxDepth) {
                        entries[entry.reading to entry.surface] = entry
                    }
                }
            }

            fun appendEntry(entry: AzooKeyDictionaryEntry, penaltyUsed: Int = 0) {
                val reading = entry.reading.hiraganaToKatakana()
                if (reading.length > maxDepth || !normalized.startsWith(reading, startIndex = start)) {
                    return
                }
                val adjusted = AzooKeyLatticeTypoPenalty.adjustedEntryOrNull(
                    entry = entry,
                    penaltyUsed = penaltyUsed,
                    readingLength = reading.length,
                ) ?: return
                if (shouldRemove(adjusted.value, reading.length)) return
                nodes += AzooKeyLatticeNode(
                    entry = adjusted,
                    startIndex = start,
                    endIndex = start + reading.length,
                )
            }

            entries.values.forEach { appendEntry(it) }

            if (enableTypoCorrection && prefix.length > 2) {
                typoSearchers.forEach { searcher ->
                    searcher.typoPrefixMatches(prefix).forEach { (typoReading, penalty) ->
                        val typoPrefix = typoReading.hiraganaToKatakana()
                        if (!normalized.startsWith(typoPrefix, startIndex = start)) return@forEach
                        loudsLookups.forEach { lookup ->
                            lookup.exactEntries(typoReading).forEach { appendEntry(it, penalty) }
                            lookup.commonPrefixEntries(typoReading).forEach { appendEntry(it, penalty) }
                        }
                    }
                }
            }
        }
        return dedupeLatticeNodes(nodes)
    }

    private fun dedupeLatticeNodes(nodes: List<AzooKeyLatticeNode>): List<AzooKeyLatticeNode> {
        return nodes.distinctBy { node ->
            "${node.startIndex}\t${node.endIndex}\t${node.entry.reading}\t${node.entry.surface}"
        }
    }

    companion object {
        const val DEFAULT_MAX_WORD_LENGTH: Int = 20
        const val DEFAULT_MAX_PREFIX_DEPTH: Int = 8
        const val DEFAULT_MAX_ENTRIES_PER_START: Int = 64
        const val DEFAULT_VALUE_THRESHOLD: Float = -17f

        fun shouldRemove(value: Float, readingLength: Int): Boolean {
            val length = readingLength.coerceAtLeast(1)
            val margin = value - DEFAULT_VALUE_THRESHOLD
            if (margin < 0f) return true
            val penalty = -2f / length
            return penalty < -margin
        }
    }
}