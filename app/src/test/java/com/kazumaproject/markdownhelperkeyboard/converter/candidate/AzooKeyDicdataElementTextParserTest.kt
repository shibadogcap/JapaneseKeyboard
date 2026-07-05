package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AzooKeyDicdataElementTextParserTest {
    @Test
    fun parseLineReadsLoudstxt2FormattedEntry() {
        val entry = AzooKeyDicdataElementTextParser.parseLine(
            line = "ネコ\t猫\t1285\t1286\t501\t-10.4",
        )

        requireNotNull(entry)
        assertEquals("猫", entry.surface)
        assertEquals("ネコ", entry.reading)
        assertEquals(1285, entry.leftId)
        assertEquals(1286, entry.rightId)
        assertEquals(501, entry.mid)
        assertEquals(-10, entry.wordCost)
        assertEquals(-10.4f, entry.value)
        assertEquals(AzooKeyDictionarySourceKind.System, entry.sourceKind)
    }

    @Test
    fun parseLineDefaultsBlankWordRightIdAndInvalidValueLikeAzooKey() {
        val entry = AzooKeyDicdataElementTextParser.parseLine(
            line = "テスト\t\t1288\t\t501\tbroken",
            sourceKind = AzooKeyDictionarySourceKind.User,
        )

        requireNotNull(entry)
        assertEquals("テスト", entry.surface)
        assertEquals(1288, entry.leftId)
        assertEquals(1288, entry.rightId)
        assertEquals(-30, entry.wordCost)
        assertEquals(-30f, entry.value)
        assertEquals(AzooKeyDictionarySourceKind.User, entry.sourceKind)
    }

    @Test
    fun parseLineAppliesAdjustAndNeverRaisesCostAboveZero() {
        val negative = AzooKeyDicdataElementTextParser.parseLine(
            line = "タイポ\t大学生\t1285\t1285\t501\t-10\t-3",
        )
        val positive = AzooKeyDicdataElementTextParser.parseLine(
            line = "ゼロ\tゼロ\t1285\t1285\t501\t-1\t3",
        )

        requireNotNull(negative)
        requireNotNull(positive)
        assertEquals(-13, negative.wordCost)
        assertEquals(-13f, negative.value)
        assertEquals(0, positive.wordCost)
        assertEquals(0f, positive.value)
    }

    @Test
    fun parseLinesCanFeedEntryIndex() {
        val entries = AzooKeyDicdataElementTextParser.parseLines(
            text = """
            ア	亜	1285	1285	501	-8
            アイ	愛	1285	1285	501	-10
            invalid
            """.trimIndent(),
        )
        val index = AzooKeyDictionaryEntryIndex(entries)

        assertEquals(
            listOf("亜"),
            index.searchPrefix("ア", limit = 1).map { it.surface },
        )
    }

    @Test
    fun parseLineRejectsBlankAndShortLines() {
        assertNull(AzooKeyDicdataElementTextParser.parseLine(""))
        assertNull(AzooKeyDicdataElementTextParser.parseLine("ア\t亜"))
    }
}
