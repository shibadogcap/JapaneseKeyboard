package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCharIdMap
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryConnectionIdResolver
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionaryRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionarySearcher
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyTemporalLearningMemoryTrie
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostBinaryParser
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyDicdataStoreUtils
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyGraphRegisteredNode
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLattice
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeDualIndexMap
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIndex
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeRange
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLoudsBackedDicdataStore
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLoudsTypoSearcher
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyMovingTowardPrefixSearch
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyMutableLatticeNode
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyTypoCorrectionGenerator
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyWiseDicdata

/**
 * AzooKey [DicdataStore](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当の facade。
 */
class AzooKeyDicdataFacade(
    private val loudsLookups: List<AzooKeyLoudsDictionarySearcher>,
    private val searchMemory: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
    private val typoSearchers: List<AzooKeyLoudsTypoSearcher> = emptyList(),
    private val connectionStore: AzooKeyConnectionCostStore?,
    private val temporalMemoryTrie: AzooKeyTemporalLearningMemoryTrie? = null,
) {
    private val registry = loudsLookups.filterIsInstance<AzooKeyLoudsDictionaryRegistry>().firstOrNull()
    private val connectionIdResolver = AzooKeyDictionaryConnectionIdResolver()
    private val charIdMap: AzooKeyCharIdMap? = registry?.charIdMap()

    fun getConnectionCost(formerRightId: Int, latterLeftId: Int): Float {
        return connectionStore?.getConnectionCost(formerRightId, latterLeftId) ?: DEFAULT_CC
    }

    fun getCCLatter(formerRightId: Int): FloatArray {
        return connectionStore?.getCCLatter(formerRightId)
            ?: FloatArray(AzooKeyConnectionCostBinaryParser.CID_COUNT) { DEFAULT_CC }
    }

    fun getMMValue(formerMid: Int, latterMid: Int): Float {
        return connectionStore?.getMorphologicalCost(formerMid, latterMid) ?: 0f
    }

    fun shouldBeRemoved(entry: AzooKeyDictionaryEntry): Boolean {
        return AzooKeyLoudsBackedDicdataStore.shouldRemove(entry.value, entry.reading.length)
    }

    /**
     * AzooKey [DicdataStore.getPredictionLOUDSDicdata](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
     */
    suspend fun getPredictionLOUDSDicdata(
        key: String,
        useMemory: Boolean,
        includeExactMatch: Boolean = false,
    ): List<AzooKeyDictionaryEntry> {
        val normalized = key.hiraganaToKatakana()
        if (normalized.isEmpty()) return emptyList()
        val maxCount = 700
        val depth = when (normalized.length) {
            1 -> 3
            2 -> 5
            else -> Int.MAX_VALUE
        }
        val result = mutableListOf<AzooKeyDictionaryEntry>()

        val systemEntries = registry?.prefixEntries(normalized, maxDepth = depth, maxCount = maxCount)
            ?: loudsLookups.flatMap { it.prefixEntries(normalized, maxDepth = depth, maxCount = maxCount) }
        result += systemEntries.filter { entry ->
            AzooKeyDicdataStoreUtils.predictionUsable.getOrNull(entry.rightId ?: 0) != false
        }

        if (includeExactMatch && result.size < maxCount) {
            val exact = registry?.exactEntries(normalized)
                ?: loudsLookups.flatMap { it.exactEntries(normalized) }
            result += exact.filter { entry ->
                AzooKeyDicdataStoreUtils.predictionUsable.getOrNull(entry.rightId ?: 0) != false
            }
        }

        registry?.let { reg ->
            result += reg.prefixEntriesForIdentifier("user", normalized, maxDepth = depth, maxCount = maxCount)
            if (includeExactMatch && result.size < maxCount) {
                result += reg.exactEntriesForIdentifier("user", normalized)
            }
            if (useMemory) {
                result += reg.prefixEntriesForIdentifier("memory", normalized, maxDepth = depth, maxCount = maxCount)
                if (includeExactMatch && result.size < maxCount) {
                    result += reg.exactEntriesForIdentifier("memory", normalized)
                }
                result += searchMemory(normalized, maxCount)
            }
        }

        return result.distinctBy { it.surface to it.reading }
    }

    fun getPerfectMatchedUserShortcuts(ruby: String): List<AzooKeyDictionaryEntry> {
        val normalized = ruby.hiraganaToKatakana()
        if (normalized.isEmpty()) return emptyList()
        return registry?.exactEntriesForIdentifier("user_shortcuts", normalized).orEmpty()
    }

    suspend fun buildLattice(
        composingText: ComposingText,
        needTypoCorrection: Boolean,
        useMemory: Boolean,
    ): AzooKeyLattice {
        val inputCount = composingText.input.size
        val surfaceCount = composingText.convertTarget.length
        val indexMap = AzooKeyLatticeDualIndexMap(composingText)
        val latticeIndices = indexMap.indices(inputCount = inputCount, surfaceCount = surfaceCount)
        val rawNodes = latticeIndices.map { index ->
            val inputRange = index.inputIndex?.let { iIndex ->
                InputRange(startIndex = iIndex, endIndexRange = null)
            }
            val surfaceRange = index.surfaceIndex?.let { sIndex ->
                SurfaceRange(startIndex = sIndex, endIndexRange = null)
            }
            lookupDicdata(
                composingText = composingText,
                inputRange = inputRange,
                surfaceRange = surfaceRange,
                needTypoCorrection = needTypoCorrection,
                useMemory = useMemory,
            )
        }
        return AzooKeyLattice(
            inputCount = inputCount,
            surfaceCount = surfaceCount,
            rawNodes = rawNodes,
        )
    }

    suspend fun lookupDicdata(
        composingText: ComposingText,
        inputRange: InputRange?,
        surfaceRange: SurfaceRange?,
        needTypoCorrection: Boolean,
        useMemory: Boolean,
    ): List<AzooKeyMutableLatticeNode> {
        if (inputRange == null && surfaceRange == null) return emptyList()

        val inputProcessRange = inputRange?.let { range ->
            val toInputIndexLeft = range.endIndexRange?.first ?: range.startIndex
            val toInputIndexRight = minOf(
                range.endIndexRange?.last?.plus(1) ?: composingText.input.size,
                range.startIndex + MAX_LENGTH,
            )
            if (range.startIndex > toInputIndexLeft || toInputIndexLeft >= toInputIndexRight) {
                return emptyList()
            }
            AzooKeyTypoCorrectionGenerator.ProcessRange(
                leftIndex = range.startIndex,
                rightRangeStart = toInputIndexLeft,
                rightRangeEndExclusive = toInputIndexRight,
            )
        }

        val surfaceProcessRange = surfaceRange?.let { range ->
            val toSurfaceIndexLeft = range.endIndexRange?.first ?: range.startIndex
            val toSurfaceIndexRight = minOf(
                range.endIndexRange?.last?.plus(1) ?: composingText.convertTarget.length,
                range.startIndex + MAX_LENGTH,
            )
            if (range.startIndex > toSurfaceIndexLeft || toSurfaceIndexLeft >= toSurfaceIndexRight) {
                return emptyList()
            }
            AzooKeyTypoCorrectionGenerator.ProcessRange(
                leftIndex = range.startIndex,
                rightRangeStart = toSurfaceIndexLeft,
                rightRangeEndExclusive = toSurfaceIndexRight,
            )
        }

        val needBOS = inputRange?.startIndex == 0 || surfaceRange?.startIndex == 0
        val latticeNodes = mutableListOf<AzooKeyMutableLatticeNode>()

        fun appendNode(entry: AzooKeyDictionaryEntry, endIndex: AzooKeyLatticeIndex) {
            val range = when (endIndex) {
                is AzooKeyLatticeIndex.Input -> AzooKeyLatticeRange.Input(
                    from = checkNotNull(inputRange).startIndex,
                    to = endIndex.value + 1,
                )
                is AzooKeyLatticeIndex.Surface -> AzooKeyLatticeRange.Surface(
                    from = checkNotNull(surfaceRange).startIndex,
                    to = endIndex.value + 1,
                )
            }
            val resolved = connectionIdResolver.resolve(entry)
            val node = AzooKeyMutableLatticeNode(entry = resolved, range = range)
            if (needBOS) {
                node.prevs.add(AzooKeyGraphRegisteredNode.BOS)
            }
            latticeNodes += node
        }

        val reg = registry
        val map = charIdMap
        if (reg != null && map != null) {
            val movingResult = AzooKeyMovingTowardPrefixSearch.search(
                composingText = composingText,
                inputProcessRange = inputProcessRange,
                surfaceProcessRange = surfaceProcessRange,
                needTypoCorrection = needTypoCorrection,
                useMemory = useMemory,
                registry = reg,
                charIdMap = map,
                temporalMemoryTrie = if (useMemory) temporalMemoryTrie else null,
            )

            for (entry in movingResult.additionalEntries) {
                val info = movingResult.readingsToInfo[entry.reading.hiraganaToKatakana()] ?: continue
                val adjusted = AzooKeyMovingTowardPrefixSearch.penalizedEntryOrNull(
                    entry = entry,
                    rubyCount = entry.reading.length,
                    penalty = info.penalty,
                ) ?: continue
                appendNode(adjusted, info.endIndex)
            }

            for ((identifier, indices) in movingResult.identifierIndices) {
                val items = reg.entriesForIdentifier(identifier, indices)
                for (entry in items) {
                    val info = movingResult.readingsToInfo[entry.reading.hiraganaToKatakana()] ?: continue
                    val adjusted = AzooKeyMovingTowardPrefixSearch.penalizedEntryOrNull(
                        entry = entry,
                        rubyCount = entry.reading.length,
                        penalty = info.penalty,
                    ) ?: continue
                    appendNode(adjusted, info.endIndex)
                }
            }
        } else {
            val normalized = composingText.convertTarget.hiraganaToKatakana()
            val flatStore = AzooKeyLoudsBackedDicdataStore(
                loudsLookups = loudsLookups,
                searchMemory = searchMemory,
                typoSearchers = typoSearchers,
                enableTypoCorrection = needTypoCorrection,
            )
            val startIndices = buildList {
                surfaceRange?.let { range ->
                    val endExclusive = range.endIndexRange?.last?.plus(1) ?: normalized.length
                    addAll(range.startIndex until endExclusive.coerceAtMost(normalized.length))
                }
                if (isEmpty() && inputRange != null) {
                    add(inputRange.startIndex.coerceAtMost(normalized.length - 1).coerceAtLeast(0))
                }
            }
            flatStore.buildLatticeNodesForStarts(
                normalized = normalized,
                useMemory = useMemory,
                startIndices = startIndices,
                composingText = composingText,
            ).forEach { node ->
                appendNode(
                    entry = node.entry,
                    endIndex = AzooKeyLatticeIndex.Surface(node.endIndex),
                )
            }
        }

        if (surfaceProcessRange != null) {
            val chars = composingText.convertTarget.hiraganaToKatakana()
            var segment = chars.substring(
                surfaceProcessRange.leftIndex,
                surfaceProcessRange.rightRangeStart,
            )
            for (i in surfaceProcessRange.rightIndexRange) {
                segment += chars[i]
                val wiseEntries = AzooKeyWiseDicdata.generate(
                    convertTarget = segment,
                    surfaceRange = surfaceProcessRange.leftIndex..i,
                    fullText = chars,
                )
                for (entry in wiseEntries) {
                    appendNode(entry, AzooKeyLatticeIndex.Surface(i))
                }
            }
        }

        return latticeNodes
    }

    data class InputRange(val startIndex: Int, val endIndexRange: IntRange?)
    data class SurfaceRange(val startIndex: Int, val endIndexRange: IntRange?)

    companion object {
        private const val DEFAULT_CC = -25f
        private const val MAX_LENGTH = 20
    }
}
