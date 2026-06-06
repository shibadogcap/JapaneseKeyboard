package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyTemporalLearningMemoryTrieTest {
    @Test
    fun prefixMatchReturnsDescendants() {
        val trie = AzooKeyTemporalLearningMemoryTrie()
        trie.memorize(
            AzooKeyDictionaryEntryMapper.memory(
                surface = "司会",
                reading = "しかい",
                leftId = null,
                rightId = null,
                legacyScore = 10,
                readingLength = 3,
            ),
            listOf(0, 1, 2),
        )
        val results = trie.prefixMatch(listOf(0, 1))
        assertEquals(listOf("司会"), results.map { it.surface })
    }
}