package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyEmojiDicdataTextParserTest {
    @Test
    fun parseReadsEmojiDicdataAndNormalizesReading() {
        val entries = AzooKeyEmojiDicdataTextParser.parse(
            """
            エガオ	😀️	5	5	501	-20
            カオ	😀️	5	5	501	-20
            broken
            """.trimIndent()
        )

        assertEquals(
            listOf("えがお:😀️:-20", "かお:😀️:-20"),
            entries.map { "${it.reading}:${it.surface}:${it.wordCost}" },
        )
        assertEquals(
            listOf(AzooKeyDictionarySourceKind.Emoji),
            entries.map { it.sourceKind }.distinct(),
        )
        assertTrue(entries.all { AzooKeyDictionaryMetadata.EmojiDicdata in it.metadata })
    }
}
