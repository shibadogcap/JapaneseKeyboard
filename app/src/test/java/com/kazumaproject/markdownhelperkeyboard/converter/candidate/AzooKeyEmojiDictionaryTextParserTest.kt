package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyEmojiDictionaryTextParserTest {
    @Test
    fun parseReadsAzooKeyEmojiDictionaryTsvFormat() {
        val entries = AzooKeyEmojiDictionaryTextParser.parse(
            """
            😀	えもじ,EMOJI	😃,😄
            🍣	すし	
            broken
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "えもじ:😀",
                "えもじ:😃",
                "えもじ:😄",
                "emoji:😀",
                "emoji:😃",
                "emoji:😄",
                "すし:🍣",
            ),
            entries.map { "${it.reading}:${it.surface}" },
        )
        assertEquals(
            listOf(AzooKeyDictionarySourceKind.Emoji),
            entries.map { it.sourceKind }.distinct(),
        )
        assertEquals(
            listOf(AzooKeyCid.SYMBOL),
            entries.map { it.leftId }.distinct(),
        )
        assertEquals(
            listOf(false, true, true, false, true, true, false),
            entries.map { AzooKeyDictionaryMetadata.EmojiVariation in it.metadata },
        )
    }

    @Test
    fun parsedEntriesCanBackExactAndPrefixSearch() {
        val index = AzooKeyDictionaryEntryIndex(
            AzooKeyEmojiDictionaryTextParser.parse(
                """
                🎂	ケーキ,たんじょうび	🥳
                🍣	すし	
                """.trimIndent()
            )
        )

        assertEquals(
            listOf("🎂", "🥳"),
            index.searchExact(
                reading = "けーき",
                sourceKind = AzooKeyDictionarySourceKind.Emoji,
            ).map { it.surface },
        )
        assertEquals(
            listOf("🎂", "🥳"),
            index.searchPrefix(
                prefix = "たん",
                sourceKind = AzooKeyDictionarySourceKind.Emoji,
            ).map { it.surface },
        )
    }
}
