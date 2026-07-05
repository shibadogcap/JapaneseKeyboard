package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyTextReplacerTest {
    @Test
    fun getSearchResultUsesExactNormalizedQueryLikeAzooKey() {
        val replacer = AzooKeyTextReplacer.fromEmojiTextReplacerText(
            """
            🦀️	かに,カニ,蟹	
            🎂	ケーキ,たんじょうび	🥳
            """.trimIndent()
        )

        assertFalse(replacer.isEmpty)
        assertEquals(
            listOf("🦀️"),
            replacer.getSearchResult("カニ").map { it.text },
        )
        assertEquals(
            listOf("🎂"),
            replacer.getSearchResult("たんじょうび", ignoreNonBaseEmoji = true).map { it.text },
        )
        assertTrue(replacer.getSearchResult("か").isEmpty())
    }

    @Test
    fun getSearchResultIgnoresVariationEmojiWhenRequested() {
        val replacer = AzooKeyTextReplacer.fromEmojiTextReplacerText(
            "🎂\tケーキ,たんじょうび\t🥳"
        )

        assertEquals(
            listOf("🎂"),
            replacer.getSearchResult("ケーキ", ignoreNonBaseEmoji = true).map { it.text },
        )
        assertEquals(
            listOf("🎂", "🥳"),
            replacer.getSearchResult("ケーキ", ignoreNonBaseEmoji = false).map { it.text },
        )
    }

    @Test
    fun getReplacementCandidateSuggestsEmojiVariations() {
        val replacer = AzooKeyTextReplacer.fromEmojiTextReplacerText(
            "🎂\tケーキ,たんじょうび\t🥳"
        )

        assertEquals(
            listOf("🥳"),
            replacer.getReplacementCandidate(left = "", center = "🎂", right = "").map { it.text },
        )
    }
}
