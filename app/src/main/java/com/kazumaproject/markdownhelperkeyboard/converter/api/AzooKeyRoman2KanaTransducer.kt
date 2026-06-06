package com.kazumaproject.markdownhelperkeyboard.converter.api

/**
 * AzooKey [ComposingText](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の Roman2Kana 変換。
 * IME の [RomajiKanaConverter.convert] と同じ最長一致ルール（オフライン・Android 非依存）。
 */
class AzooKeyRoman2KanaTransducer(
    private val romajiToKana: Map<String, Pair<String, Int>>,
) {
    private val maxKeyLength: Int = romajiToKana.keys.maxOfOrNull { it.length } ?: 1

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
    }
}