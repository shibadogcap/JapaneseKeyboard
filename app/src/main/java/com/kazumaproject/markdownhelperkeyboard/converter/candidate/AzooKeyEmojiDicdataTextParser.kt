package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.toHiragana

object AzooKeyEmojiDicdataTextParser {
    fun parse(text: String): List<AzooKeyDictionaryEntry> {
        return AzooKeyDicdataElementTextParser.parseLines(
            text = text,
            sourceKind = AzooKeyDictionarySourceKind.Emoji,
        )
            .map { entry ->
                entry.copy(
                    reading = entry.reading.lowercase().toHiragana(),
                    metadata = entry.metadata + AzooKeyDictionaryMetadata.EmojiDicdata,
                )
            }
            .distinctBy { "${it.reading}\u0000${it.surface}" }
    }
}
