package com.kazumaproject.markdownhelperkeyboard.converter.lattice

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

        return result
    }
}
