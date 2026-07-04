package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana

/**
 * AzooKey [ComposingText](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の Roman2Kana 変換。
 * IME の [RomajiKanaConverter.convert] と同じ最長一致ルール（オフライン・Android 非依存）。
 */
class AzooKeyRoman2KanaTransducer(
    private val romajiToKana: Map<String, Pair<String, Int>>,
) {
    private val maxKeyLength: Int = romajiToKana.keys.maxOfOrNull { it.length } ?: 1
    private val possibleNextsByPrefix: Map<String, List<String>> = buildPossibleNexts(romajiToKana)

    /** AzooKey [InputTable.possibleNexts](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
    fun possibleNexts(romanPrefix: String): List<String> =
        possibleNextsByPrefix[romanPrefix.lowercase()].orEmpty()

    fun convert(romaji: String): String {
        val text = romaji.lowercase()
        if (text.isEmpty()) return ""
        val result = StringBuilder()
        var index = 0
        while (index < text.length) {
            val current = text[index]
            if (current == 'n' && index + 1 < text.length && text[index + 1] !in "aiueoyn") {
                result.append('ん')
                index++
                continue
            }
            if (
                index + 1 < text.length &&
                current == text[index + 1] &&
                current in "kstcpbdfghjmqrvwz"
            ) {
                result.append('っ')
                index++
                continue
            }
            var matched = false
            for (len in maxKeyLength downTo 1) {
                if (index + len > text.length) continue
                val segment = text.substring(index, index + len)
                val mapping = romajiToKana[segment] ?: continue
                result.append(mapping.first)
                index += mapping.second
                matched = true
                break
            }
            if (!matched) {
                result.append(current)
                index++
            }
        }
        return result.toString()
    }

    companion object {
        val Identity: AzooKeyRoman2KanaTransducer =
            AzooKeyRoman2KanaTransducer(emptyMap())

        fun fromMap(map: Map<String, Pair<String, Int>>): AzooKeyRoman2KanaTransducer {
            return AzooKeyRoman2KanaTransducer(map)
        }

        /** AzooKey [InputTables.defaultRoman2Kana](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
        fun fromDefaultInputTable(map: Map<String, Pair<String, Int>>): AzooKeyRoman2KanaTransducer =
            fromMap(map)

        /** AzooKey InputTable.possibleNexts 構築（roman prefix → katakana 変換候補列） */
        internal fun buildPossibleNexts(map: Map<String, Pair<String, Int>>): Map<String, List<String>> {
            if (map.isEmpty()) return emptyMap()
            val results = mutableMapOf<String, MutableList<String>>()
            for ((key, value) in map) {
                val katakana = value.first.hiraganaToKatakana()
                for (prefixCount in 1 until key.length) {
                    val prefix = key.substring(0, prefixCount)
                    results.getOrPut(prefix) { mutableListOf() }.add(katakana)
                }
            }
            return results
        }
    }
}