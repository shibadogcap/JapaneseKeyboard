package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyDictionaryEntryIndexTest {
    @Test
    fun searchExactReturnsSourceFilteredEntriesSortedByPValue() {
        val index = AzooKeyDictionaryEntryIndex(
            listOf(
                AzooKeyDictionaryEntryMapper.emoji("🍺", "びーる", score = -1),
                AzooKeyDictionaryEntryMapper.symbol("※", "びーる", score = -100),
                AzooKeyDictionaryEntryMapper.emoji("🍻", "びーる", score = -5),
            )
        )

        val result = index.searchExact(
            reading = "びーる",
            sourceKind = AzooKeyDictionarySourceKind.Emoji,
            limit = 10,
        )

        assertEquals(listOf("🍺", "🍻"), result.map { it.surface })
    }

    @Test
    fun searchPrefixMergesReadingsAndRemovesDuplicateSurfaces() {
        val index = AzooKeyDictionaryEntryIndex(
            listOf(
                AzooKeyDictionaryEntryMapper.emoji("🍣", "す", score = -1),
                AzooKeyDictionaryEntryMapper.emoji("🍣", "すし", score = -10),
                AzooKeyDictionaryEntryMapper.emoji("🍺", "すごい", score = -5),
                AzooKeyDictionaryEntryMapper.emoji("🍰", "けーき", score = -100),
            )
        )

        val result = index.searchPrefix(
            prefix = "す",
            sourceKind = AzooKeyDictionarySourceKind.Emoji,
            limit = 2,
        )

        assertEquals(listOf("🍣", "🍺"), result.map { it.surface })
    }

    @Test
    fun searchReturnsEmptyForBlankOrNonPositiveLimit() {
        val index = AzooKeyDictionaryEntryIndex(
            listOf(AzooKeyDictionaryEntryMapper.emoji("😀", "えもじ"))
        )

        assertTrue(index.searchExact(" ", limit = 10).isEmpty())
        assertTrue(index.searchPrefix("え", limit = 0).isEmpty())
    }
}
