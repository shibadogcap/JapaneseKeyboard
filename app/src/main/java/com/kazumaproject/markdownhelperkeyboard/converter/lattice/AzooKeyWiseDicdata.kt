package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.extensions.toHiragana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertFullWidthNumbersToHalfWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji

/**
 * AzooKey [DicdataStore.getWiseDicdata](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
object AzooKeyWiseDicdata {
    fun generate(
        convertTarget: String,
        surfaceRange: IntRange,
        fullText: String,
    ): List<AzooKeyDictionaryEntry> {
        val result = mutableListOf<AzooKeyDictionaryEntry>()
        result += AzooKeyJapaneseNumber.getJapaneseNumberDicdata(convertTarget)

        val prevIsNumber = surfaceRange.first > 0 && fullText.getOrNull(surfaceRange.first - 1)?.isDigit() == true
        val nextIsNumber = surfaceRange.last + 1 < fullText.length &&
            fullText.getOrNull(surfaceRange.last + 1)?.isDigit() == true
        if (!prevIsNumber && !nextIsNumber) {
            val normalized = convertTarget.convertFullWidthNumbersToHalfWidth()
            normalized.toLongOrNull()?.let { numberVal ->
                result += AzooKeyDictionaryEntry(
                    surface = convertTarget,
                    reading = convertTarget,
                    leftId = AzooKeyCid.NUMBER,
                    rightId = AzooKeyCid.NUMBER,
                    mid = AzooKeyMid.NUMBER,
                    wordCost = 14,
                    value = -14f,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                )
                if (numberVal in -1_000_000_000_000L..1_000_000_000_000L) {
                    try {
                        result += AzooKeyDictionaryEntry(
                            surface = numberVal.toKanji(),
                            reading = convertTarget,
                            leftId = AzooKeyCid.NUMBER,
                            rightId = AzooKeyCid.NUMBER,
                            mid = AzooKeyMid.NUMBER,
                            wordCost = 16,
                            value = -16f,
                            sourceKind = AzooKeyDictionarySourceKind.System,
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (convertTarget.all { it in 'a'..'z' || it in 'A'..'Z' || it in 'ａ'..'ｚ' || it in 'Ａ'..'Ｚ' }) {
            result += AzooKeyDictionaryEntry(
                surface = convertTarget,
                reading = convertTarget,
                leftId = AzooKeyCid.PROPER_NOUN,
                rightId = AzooKeyCid.PROPER_NOUN,
                mid = AzooKeyMid.GENERAL,
                wordCost = 14,
                value = -14f,
                sourceKind = AzooKeyDictionarySourceKind.System,
            )
        }

        // 1文字入力のリテラル通過（記号・カナ変換不能文字など）
        if (convertTarget.length == 1) {
            val katakana = convertTarget.hiraganaToKatakana()
            val hiragana = convertTarget.toHiragana()
            if (katakana == hiragana) {
                result += literalEntry(surface = katakana, reading = katakana, value = -14f)
            } else {
                result += literalEntry(surface = hiragana, reading = katakana, value = -13f)
                result += literalEntry(surface = katakana, reading = katakana, value = -14f)
            }

            val first = convertTarget.first()
            var symbolValue = -14f
            val halfwidth = fullwidthToHalfwidth[first]
            if (halfwidth != null && halfwidth != first) {
                result += literalEntry(
                    surface = convertTarget,
                    reading = convertTarget,
                    leftId = AzooKeyCid.SYMBOL,
                    value = symbolValue,
                )
                symbolValue -= 5f
                result += literalEntry(
                    surface = halfwidth.toString(),
                    reading = convertTarget,
                    leftId = AzooKeyCid.SYMBOL,
                    value = symbolValue,
                )
                symbolValue -= 5f
            }
            val fullwidth = halfwidthToFullwidth[first]
            if (fullwidth != null && fullwidth != first) {
                result += literalEntry(
                    surface = convertTarget,
                    reading = convertTarget,
                    leftId = AzooKeyCid.SYMBOL,
                    value = symbolValue,
                )
                symbolValue -= 5f
                result += literalEntry(
                    surface = fullwidth.toString(),
                    reading = convertTarget,
                    leftId = AzooKeyCid.SYMBOL,
                    value = symbolValue,
                )
            }
        }

        return result
    }

    private fun literalEntry(
        surface: String,
        reading: String,
        leftId: Int = AzooKeyCid.PROPER_NOUN,
        value: Float,
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = leftId,
            rightId = leftId,
            mid = AzooKeyMid.GENERAL,
            wordCost = value.toInt(),
            value = value,
            sourceKind = AzooKeyDictionarySourceKind.System,
        )
    }

    private val fullwidthToHalfwidth: Map<Char, Char> = mapOf(
        '（' to '(', '）' to ')', '［' to '[', '］' to ']', '｛' to '{', '｝' to '}',
        '＜' to '<', '＞' to '>', '「' to '「', '」' to '」', '『' to '『', '』' to '』',
        '＋' to '+', '－' to '-', '＊' to '*', '＝' to '=', '！' to '!', '＃' to '#',
        '％' to '%', '＆' to '&', '＠' to '@', '；' to ';', '：' to ':', '，' to ',',
        '．' to '.', '／' to '/', '＿' to '_', '￥' to '\\',
    )

    private val halfwidthToFullwidth: Map<Char, Char> =
        fullwidthToHalfwidth.entries.associate { (full, half) -> half to full }
}
