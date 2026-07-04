package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText

/**
 * AzooKey [DicdataStore.movingTowardPrefixSearch](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の Kotlin port。
 */
object AzooKeyMovingTowardPrefixSearch {
    data class PrefixInfo(
        val katakana: String,
        val endSurfaceIndex: Int,
        val penalty: Float,
    )

    data class Result(
        val readings: Map<String, PrefixInfo>,
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

        fun next(): Pair<String, PrefixInfo>? {
            surfaceGenerator?.next()?.let { return it }
            typoGenerator?.next()?.let { reading ->
                return reading.katakana to PrefixInfo(
                    katakana = reading.katakana,
                    endSurfaceIndex = reading.endSurfaceIndex,
                    penalty = reading.penalty.toFloat(),
                )
            }
            return null
        }

        class SurfaceGenerator(
            private val surface: String,
            private var range: AzooKeyTypoCorrectionGenerator.ProcessRange,
        ) {
            private var currentIndex: Int = range.lowerBound

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
                        rightIndexExclusive = minOf(range.upperBound, targetUpper),
                    )
                }
            }

            fun next(): Pair<String, PrefixInfo>? {
                if (currentIndex < range.lowerBound || currentIndex >= range.upperBound) {
                    return null
                }
                val end = currentIndex
                currentIndex++
                val segment = surface.substring(range.leftIndex, end + 1)
                return segment to PrefixInfo(
                    katakana = segment,
                    endSurfaceIndex = end,
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
        typoSearchers: List<AzooKeyLoudsTrieTypoSearcher>,
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

        val helpers = mutableMapOf<AzooKeyLoudsTrieTypoSearcher, AzooKeyLoudsMovingTowardPrefixSearchHelper>()
        val readings = linkedMapOf<String, PrefixInfo>()

        while (true) {
            val (katakana, info) = generator.next() ?: break
            if (katakana.isEmpty()) continue
            val charIds = katakana.mapNotNull { ch ->
                typoSearchers.firstOrNull()?.charIdMap?.encode(ch.toString())?.firstOrNull()
            }
            if (charIds.size != katakana.length) continue

            var updated = false
            var availableMaxIndex = 0
            for (searcher in typoSearchers) {
                val helper = helpers.getOrPut(searcher) {
                    AzooKeyLoudsMovingTowardPrefixSearchHelper(searcher.trie)
                }
                val result = helper.update(charIds)
                updated = updated || result.updated
                availableMaxIndex = maxOf(availableMaxIndex, result.availableMaxIndex)
            }
            if (availableMaxIndex < katakana.length - 1) {
                generator.setUnreachablePath(katakana.substring(0, availableMaxIndex + 1))
            }
            if (updated) {
                val existing = readings[katakana]
                if (existing == null || info.penalty < existing.penalty) {
                    readings[katakana] = info
                }
            }
        }
        return Result(readings = readings)
    }
}
