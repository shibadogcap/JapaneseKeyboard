package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionarySearcher
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertFullWidthNumbersToHalfWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji

/**
 * AzooKey [DicdataStore.lookupDicdata](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当の辞書 lookup。
 */
interface AzooKeyDicdataStore {
    suspend fun buildLatticeNodes(
        input: String,
        useMemory: Boolean,
        composingText: ComposingText? = null,
    ): List<AzooKeyLatticeNode>

    suspend fun buildLatticeNodesIncremental(
        input: String,
        useMemory: Boolean,
        previousNormalizedInput: String,
        previousNodes: List<AzooKeyLatticeNode>,
        composingText: ComposingText? = null,
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
        composingText: ComposingText?,
    ): List<AzooKeyLatticeNode> {
        val normalized = input.hiraganaToKatakana()
        if (normalized.isEmpty()) return emptyList()
        val text = composingText ?: ComposingText.fromConvertTarget(input)
        return buildLatticeNodesForStarts(
            normalized = normalized,
            useMemory = useMemory,
            startIndices = dualIndexStartPositions(text, 0 until normalized.length),
            composingText = text,
        )
    }

    override suspend fun buildLatticeNodesIncremental(
        input: String,
        useMemory: Boolean,
        previousNormalizedInput: String,
        previousNodes: List<AzooKeyLatticeNode>,
        composingText: ComposingText?,
    ): List<AzooKeyLatticeNode> {
        val normalized = input.hiraganaToKatakana()
        val previous = previousNormalizedInput.hiraganaToKatakana()
        if (
            normalized.isEmpty() ||
            !normalized.startsWith(previous) ||
            normalized.length <= previous.length
        ) {
            return buildLatticeNodes(input, useMemory, composingText)
        }
        val previousLength = previous.length
        val reused = previousNodes.filter { it.endIndex <= previousLength }
        val text = composingText ?: ComposingText.fromConvertTarget(input)
        val refreshStarts = dualIndexStartPositions(text, 0..previousLength) +
            dualIndexStartPositions(text, previousLength until normalized.length)
        val additional = buildLatticeNodesForStarts(
            normalized = normalized,
            useMemory = useMemory,
            startIndices = refreshStarts.distinct().sorted(),
            composingText = text,
        )
        return dedupeLatticeNodes(reused + additional)
    }

    internal suspend fun buildLatticeNodesForStarts(
        normalized: String,
        useMemory: Boolean,
        startIndices: Iterable<Int>,
        composingText: ComposingText? = null,
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

            // 機械的に一部のデータを生成する（数詞、アラビア数字、英単語など）
            // 開始位置から 1..maxDepth のすべての長さの部分文字列に対して実行する（本家 azooKey の getWiseDicdata に準拠）
            for (len in 1..maxDepth) {
                val segment = prefix.substring(0, len)

                // 数詞の動的生成
                AzooKeyJapaneseNumber.getJapaneseNumberDicdata(segment).forEach { entry ->
                    if (entry.reading.length <= maxDepth) {
                        entries[entry.reading to entry.surface] = entry
                    }
                }

                // アラビア数字そのもののパース
                val normalizedSegment = segment.convertFullWidthNumbersToHalfWidth()
                val numberVal = normalizedSegment.toLongOrNull()
                if (numberVal != null) {
                    val numEntry = AzooKeyDictionaryEntry(
                        surface = segment,
                        reading = segment,
                        leftId = AzooKeyCid.NUMBER,
                        rightId = AzooKeyCid.NUMBER,
                        mid = AzooKeyMid.NUMBER,
                        wordCost = 14,
                        value = -14f,
                        sourceKind = AzooKeyDictionarySourceKind.System
                    )
                    if (numEntry.reading.length <= maxDepth) {
                        entries[numEntry.reading to numEntry.surface] = numEntry
                    }

                    try {
                        val kansuji = numberVal.toKanji()
                        val kanEntry = AzooKeyDictionaryEntry(
                            surface = kansuji,
                            reading = segment,
                            leftId = AzooKeyCid.NUMBER,
                            rightId = AzooKeyCid.NUMBER,
                            mid = AzooKeyMid.NUMBER,
                            wordCost = 16,
                            value = -16f,
                            sourceKind = AzooKeyDictionarySourceKind.System
                        )
                        if (kanEntry.reading.length <= maxDepth) {
                            entries[kanEntry.reading to kanEntry.surface] = kanEntry
                        }
                    } catch (e: Exception) {
                        // Ignore kanji formatting error if any
                    }
                }

                // 英単語（アルファベット）の動的生成
                if (segment.all { it in 'a'..'z' || it in 'A'..'Z' || it in 'ａ'..'ｚ' || it in 'Ａ'..'Ｚ' }) {
                    val engEntry = AzooKeyDictionaryEntry(
                        surface = segment,
                        reading = segment,
                        leftId = AzooKeyCid.PROPER_NOUN,
                        rightId = AzooKeyCid.PROPER_NOUN,
                        mid = AzooKeyMid.GENERAL,
                        wordCost = 14,
                        value = -14f,
                        sourceKind = AzooKeyDictionarySourceKind.System
                    )
                    if (engEntry.reading.length <= maxDepth) {
                        entries[engEntry.reading to engEntry.surface] = engEntry
                    }
                }

                // ── 混在入力（数字/英字 + 仮名）のサポート ──────────────────────
                // prefix が "2000エン" のような混在文字列の場合に、先行する数字・英字部分だけのエントリを生成する
                val mixedPrefixLen = leadingAlnumLength(segment)
                if (mixedPrefixLen in 1 until segment.length && mixedPrefixLen <= maxDepth) {
                    val alnumPart = segment.substring(0, mixedPrefixLen)
                    val alnumHankaku = alnumPart.convertFullWidthNumbersToHalfWidth()
                    val numVal = alnumHankaku.toLongOrNull()
                    if (numVal != null) {
                        // 数字部分 → 数字エントリ
                        entries[alnumPart to alnumPart] = AzooKeyDictionaryEntry(
                            surface = alnumPart,
                            reading = alnumPart,
                            leftId = AzooKeyCid.NUMBER,
                            rightId = AzooKeyCid.NUMBER,
                            mid = AzooKeyMid.NUMBER,
                            wordCost = 14,
                            value = -14f,
                            sourceKind = AzooKeyDictionarySourceKind.System
                        )
                        try {
                            val kansuji = numVal.toKanji()
                            entries[kansuji to kansuji] = AzooKeyDictionaryEntry(
                                surface = kansuji,
                                reading = alnumPart,
                                leftId = AzooKeyCid.NUMBER,
                                rightId = AzooKeyCid.NUMBER,
                                mid = AzooKeyMid.NUMBER,
                                wordCost = 16,
                                value = -16f,
                                sourceKind = AzooKeyDictionarySourceKind.System
                            )
                        } catch (_: Exception) {}
                    } else {
                        // 英字部分 → 英字エントリ
                        entries[alnumPart to alnumPart] = AzooKeyDictionaryEntry(
                            surface = alnumPart,
                            reading = alnumPart,
                            leftId = AzooKeyCid.PROPER_NOUN,
                            rightId = AzooKeyCid.PROPER_NOUN,
                            mid = AzooKeyMid.GENERAL,
                            wordCost = 14,
                            value = -14f,
                            sourceKind = AzooKeyDictionarySourceKind.System
                        )
                    }
                }
            }



            fun appendEntry(
                entry: AzooKeyDictionaryEntry,
                penaltyUsed: Int = 0,
                typoReading: String? = null
            ) {
                val reading = entry.reading.hiraganaToKatakana()
                if (reading.length > maxDepth) {
                    return
                }
                if (penaltyUsed > 0 && typoReading != null) {
                    if (!typoReading.startsWith(reading)) {
                        return
                    }
                } else {
                    if (!normalized.startsWith(reading, startIndex = start)) {
                        return
                    }
                }
                val adjusted = AzooKeyLatticeTypoPenalty.adjustedEntryOrNull(
                    entry = entry,
                    penaltyUsed = penaltyUsed,
                    wordLength = entry.reading.length,
                ) ?: return
                if (shouldRemove(adjusted.value, entry.reading.length)) return
                nodes += AzooKeyLatticeNode(
                    entry = adjusted,
                    startIndex = start,
                    endIndex = start + reading.length,
                )
            }

            entries.values.forEach { appendEntry(it) }

            if (enableTypoCorrection && prefix.length > 2 && composingText != null) {
                val typoReadings = AzooKeyTypoCorrectionGenerator.collectTypoReadings(
                    composingText = composingText,
                    surfaceStart = start,
                    surfaceEndExclusive = start + prefix.length,
                )
                typoReadings.forEach { typo ->
                    loudsLookups.forEach { lookup ->
                        lookup.exactEntries(typo.katakana).forEach {
                            appendEntry(it, typo.penalty.toInt(), typo.katakana)
                        }
                        lookup.commonPrefixEntries(typo.katakana).forEach {
                            appendEntry(it, typo.penalty.toInt(), typo.katakana)
                        }
                    }
                }
                val activeTypoSearchers = if (typoSearchers.isNotEmpty()) {
                    typoSearchers
                } else {
                    loudsLookups.filterIsInstance<com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionaryRegistry>()
                        .firstOrNull()
                        ?.typoSearchersForPrefix(prefix)
                        ?: emptyList()
                }
                activeTypoSearchers.forEach { searcher ->
                    searcher.typoPrefixMatches(prefix).forEach { (typoReading, penalty) ->
                        loudsLookups.forEach { lookup ->
                            lookup.exactEntries(typoReading).forEach { appendEntry(it, penalty, typoReading) }
                            lookup.commonPrefixEntries(typoReading).forEach { appendEntry(it, penalty, typoReading) }
                        }
                    }
                }
            }
        }
        return dedupeLatticeNodes(nodes)
    }

    private fun dualIndexStartPositions(
        composingText: ComposingText,
        surfaceRange: IntRange,
    ): List<Int> {
        if (surfaceRange.isEmpty()) return emptyList()
        val indexMap = AzooKeyLatticeDualIndexMap(composingText)
        val dualIndices = indexMap.indices(
            inputCount = composingText.input.size,
            surfaceCount = composingText.convertTarget.length,
        )
        val starts = linkedSetOf<Int>()
        dualIndices.forEach { dual ->
            val surfaceIndex = dual.surfaceIndex
            if (surfaceIndex != null && surfaceIndex in surfaceRange) {
                starts += surfaceIndex
            }
        }
        if (starts.isEmpty()) {
            starts += surfaceRange.toList()
        }
        return starts.sorted()
    }

    private fun dedupeLatticeNodes(nodes: List<AzooKeyLatticeNode>): List<AzooKeyLatticeNode> {
        return nodes.distinctBy { node ->
            "${node.startIndex}\t${node.endIndex}\t${node.entry.reading}\t${node.entry.surface}"
        }
    }

    companion object {
        const val DEFAULT_MAX_WORD_LENGTH: Int = 20
        const val DEFAULT_MAX_PREFIX_DEPTH: Int = 20
        const val DEFAULT_MAX_ENTRIES_PER_START: Int = 64
        const val DEFAULT_VALUE_THRESHOLD: Float = -17f

        fun shouldRemove(value: Float, wordLength: Int): Boolean {
            val length = wordLength.coerceAtLeast(1)
            val margin = value - DEFAULT_VALUE_THRESHOLD
            if (margin < 0f) return true
            val penalty = -2f / length
            return penalty < -margin
        }

        /**
         * 文字列の先頭から連続する英数字の長さを返す。
         * e.g. "2000エン" → 4, "APIりよう" → 3, "あいう" → 0
         */
        fun leadingAlnumLength(s: String): Int {
            var len = 0
            for (ch in s) {
                when {
                    ch in '0'..'9' || ch in '０'..'９' -> len++
                    ch in 'a'..'z' || ch in 'A'..'Z' -> len++
                    ch in 'ａ'..'ｚ' || ch in 'Ａ'..'Ｚ' -> len++
                    else -> break
                }
            }
            return len
        }
    }
}