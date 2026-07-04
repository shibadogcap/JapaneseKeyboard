package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyClassicTypoCorrection

/**
 * AzooKey [TypoCorrectionGenerator](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の direct 入力向け簡易版。
 * 1 文字 = 1 [InputElement] のとき、classic typo 辞書で読み列の DFS を列挙する。
 */
object AzooKeyTypoCorrectionGenerator {
    private const val MAX_PENALTY = 10.5f

    data class ProcessRange(
        val leftIndex: Int,
        val rightIndexExclusive: Int,
    )

    data class TypoReading(
        val katakana: String,
        val penalty: Int,
    )

    fun generateDirectTypoReadings(
        composingText: ComposingText,
        range: ProcessRange,
    ): List<TypoReading> {
        if (!AzooKeyClassicTypoCorrection.isDirectOnlyInput(composingText.input)) {
            return emptyList()
        }
        if (range.leftIndex < 0 || range.rightIndexExclusive > composingText.convertTarget.length) {
            return emptyList()
        }
        val segment = composingText.convertTarget
            .substring(range.leftIndex, range.rightIndexExclusive)
            .hiraganaToKatakana()
        if (segment.length <= 2) return emptyList()
        return generateKatakanaTypoReadings(segment)
    }

    internal fun generateKatakanaTypoReadings(katakana: String): List<TypoReading> {
        if (katakana.length <= 2) return emptyList()
        val bestPenalty = linkedMapOf<String, Float>()

        fun record(path: String, penalty: Float) {
            if (penalty <= 0f || path.isEmpty()) return
            val existing = bestPenalty[path]
            if (existing == null || penalty < existing) {
                bestPenalty[path] = penalty
            }
        }

        fun dfs(index: Int, penalty: Float, buffer: StringBuilder) {
            if (index >= katakana.length) {
                record(buffer.toString(), penalty)
                return
            }
            if (penalty >= MAX_PENALTY) {
                buffer.append(katakana[index])
                dfs(index + 1, penalty, buffer)
                buffer.setLength(buffer.length - 1)
                return
            }

            val original = katakana[index].toString()
            buffer.append(katakana[index])
            dfs(index + 1, penalty, buffer)
            buffer.setLength(buffer.length - 1)

            for (variant in AzooKeyClassicTypoCorrection.directTypoVariants(original)) {
                val nextPenalty = penalty + variant.weight
                buffer.append(variant.katakana)
                dfs(index + 1, nextPenalty, buffer)
                buffer.setLength(buffer.length - 1)
            }
        }

        dfs(index = 0, penalty = 0f, buffer = StringBuilder())
        return bestPenalty.entries.map { (reading, penalty) ->
            TypoReading(
                katakana = reading,
                penalty = penalty.toInt().coerceAtLeast(1),
            )
        }
    }

    fun inputProcessRange(
        startIndex: Int,
        endIndexExclusive: Int,
        inputCount: Int,
        maxLength: Int = 20,
    ): ProcessRange? {
        val toRight = minOf(endIndexExclusive, startIndex + maxLength, inputCount)
        if (startIndex >= toRight) return null
        return ProcessRange(leftIndex = startIndex, rightIndexExclusive = toRight)
    }

    fun surfaceProcessRange(
        startIndex: Int,
        endIndexExclusive: Int,
        surfaceCount: Int,
        maxLength: Int = 20,
    ): ProcessRange? {
        val toRight = minOf(endIndexExclusive, startIndex + maxLength, surfaceCount)
        if (startIndex >= toRight) return null
        return ProcessRange(leftIndex = startIndex, rightIndexExclusive = toRight)
    }

    fun isRoman2KanaCompatibleInput(elements: List<com.kazumaproject.markdownhelperkeyboard.converter.api.InputElement>): Boolean =
        elements.isNotEmpty() && elements.all {
            it.inputStyle == InputStyle.Roman2Kana || it.inputStyle == InputStyle.Direct
        }
}
