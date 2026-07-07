package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCharIdMap
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionaryRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyTemporalLearningMemoryTrie

/**
 * AzooKey [DicdataStore.movingTowardPrefixSearch](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の Kotlin port。
 */
object AzooKeyMovingTowardPrefixSearch {
    data class ReadingInfo(
        val endIndex: AzooKeyLatticeIndex,
        val penalty: Float,
    )

    data class Result(
        val readingsToInfo: Map<String, ReadingInfo>,
        val identifierIndices: List<Pair<String, List<Int>>>,
        val additionalEntries: List<AzooKeyDictionaryEntry>,
    )

    private class UnifiedGenerator(
        private val roman2Kana: AzooKeyRoman2KanaTransducer,
    ) {
        private var typoGenerator: AzooKeyTypoCorrectionGenerator? = null
        private var surfaceGenerator: SurfaceGenerator? = null

        fun register(generator: AzooKeyTypoCorrectionGenerator) {
            typoGenerator = generator
        }

        fun register(generator: SurfaceGenerator) {
            surfaceGenerator = generator
        }

        fun setUnreachablePath(target: String) {
            typoGenerator?.setUnreachablePath(target)
            surfaceGenerator?.setUnreachablePath(target)
        }

        fun next(): Pair<String, ReadingInfo>? {
            surfaceGenerator?.next()?.let { return it }
            typoGenerator?.next()?.let { reading ->
                return reading.katakana to ReadingInfo(
                    endIndex = reading.endIndex,
                    penalty = reading.penalty,
                )
            }
            return null
        }

        class SurfaceGenerator(
            private val surface: String,
            private var range: AzooKeyTypoCorrectionGenerator.ProcessRange,
        ) {
            private var currentIndex: Int = range.rightRangeStart

            fun setUnreachablePath(target: String) {
                val suffix = surface.substring(range.leftIndex)
                var matched = 0
                for (ch in target) {
                    if (matched >= suffix.length || suffix[matched] != ch) return
                    matched++
                }
                if (matched == target.length) {
                    val targetUpper = range.leftIndex + target.length
                    range = AzooKeyTypoCorrectionGenerator.ProcessRange(
                        leftIndex = range.leftIndex,
                        rightRangeStart = minOf(range.rightRangeStart, targetUpper),
                        rightRangeEndExclusive = minOf(range.rightRangeEndExclusive, targetUpper),
                    )
                }
            }

            fun next(): Pair<String, ReadingInfo>? {
                if (currentIndex !in range.rightIndexRange) {
                    return null
                }
                val end = currentIndex
                currentIndex++
                val segment = surface.substring(range.leftIndex, end + 1)
                return segment to ReadingInfo(
                    endIndex = AzooKeyLatticeIndex.Surface(end),
                    penalty = 0f,
                )
            }
        }
    }

    fun search(
        composingText: ComposingText,
        inputProcessRange: AzooKeyTypoCorrectionGenerator.ProcessRange?,
        surfaceProcessRange: AzooKeyTypoCorrectionGenerator.ProcessRange?,
        needTypoCorrection: Boolean,
        useMemory: Boolean,
        registry: AzooKeyLoudsDictionaryRegistry,
        charIdMap: AzooKeyCharIdMap,
        temporalMemoryTrie: AzooKeyTemporalLearningMemoryTrie? = null,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): Result {
        val generator = UnifiedGenerator(roman2Kana)
        val surfaceKatakana = composingText.convertTarget.hiraganaToKatakana()
        if (surfaceProcessRange != null) {
            generator.register(
                UnifiedGenerator.SurfaceGenerator(
                    surface = surfaceKatakana,
                    range = surfaceProcessRange,
                ),
            )
        }
        if (inputProcessRange != null && needTypoCorrection) {
            generator.register(
                AzooKeyTypoCorrectionGenerator(
                    inputs = composingText.input,
                    range = inputProcessRange,
                    roman2Kana = roman2Kana,
                ),
            )
        }

        val targetLouds = mutableMapOf<String, AzooKeyLoudsMovingTowardPrefixSearchHelper>()
        val readingsToInfo = linkedMapOf<String, ReadingInfo>()
        val additionalEntries = mutableListOf<AzooKeyDictionaryEntry>()
        val dynamicByDepth = mutableMapOf<Int, MutableList<AzooKeyDictionaryEntry>>()

        while (true) {
            val (katakana, info) = generator.next() ?: break
            if (katakana.isEmpty()) continue
            val charIds = charIdMap.encodeAllowingUnknown(katakana)

            val firstChar = katakana.first().toString()
            val keys = if (useMemory) {
                listOf(firstChar, "user", "memory")
            } else {
                listOf(firstChar, "user")
            }

            var updated = false
            var availableMaxIndex = -1
            for (key in keys) {
                val searcher = registry.trieTypoSearcherForIdentifier(key) ?: continue
                val helper = targetLouds.getOrPut(key) {
                    AzooKeyLoudsMovingTowardPrefixSearchHelper(searcher.trie)
                }
                val result = helper.update(charIds)
                updated = updated || result.updated
                availableMaxIndex = maxOf(availableMaxIndex, result.availableMaxIndex)
            }

            temporalMemoryTrie?.let { trie ->
                val byteIds = charIds.map { it.toByte() }
                val (dicdataByDepth, memoryMaxIndex) = trie.movingTowardPrefixSearch(byteIds)
                updated = updated || dicdataByDepth.isNotEmpty()
                availableMaxIndex = maxOf(availableMaxIndex, memoryMaxIndex)
                for ((depth, entries) in dicdataByDepth) {
                    for (entry in entries) {
                        val adjusted = penalizedEntryOrNull(entry, entry.reading.length, info.penalty)
                        if (adjusted == null) continue
                        if (info.penalty == 0f) {
                            dynamicByDepth.getOrPut(depth) { mutableListOf() } += adjusted
                        } else {
                            dynamicByDepth.getOrPut(depth) { mutableListOf() } += adjusted
                        }
                    }
                }
            }

            if (availableMaxIndex > 0 && availableMaxIndex < katakana.length - 1) {
                generator.setUnreachablePath(katakana.substring(0, availableMaxIndex + 1))
            }
            if (updated) {
                val existing = readingsToInfo[katakana]
                readingsToInfo[katakana] = when {
                    existing == null || info.penalty < existing.penalty -> info
                    info.penalty > existing.penalty -> existing
                    info.endIndex is AzooKeyLatticeIndex.Surface -> info
                    else -> existing
                }
            }
        }

        val minCount = readingsToInfo.keys.minOfOrNull { it.length } ?: 0
        for ((depth, entries) in dynamicByDepth) {
            if (minCount < depth + 1) {
                additionalEntries += entries
            }
        }

        val identifierIndices = targetLouds.map { (identifier, helper) ->
            identifier to helper.indicesInDepth((minCount - 1).coerceAtLeast(0)..Int.MAX_VALUE)
        }

        return Result(
            readingsToInfo = readingsToInfo,
            identifierIndices = identifierIndices,
            additionalEntries = additionalEntries,
        )
    }

    internal fun penalizedEntryOrNull(
        entry: AzooKeyDictionaryEntry,
        rubyCount: Int,
        penalty: Float,
    ): AzooKeyDictionaryEntry? {
        if (penalty == 0f) return entry
        return AzooKeyLatticeTypoPenalty.adjustedEntryOrNull(
            entry = entry,
            penaltyUsed = penalty.toInt().coerceAtLeast(1),
            wordLength = rubyCount,
        )
    }
}
