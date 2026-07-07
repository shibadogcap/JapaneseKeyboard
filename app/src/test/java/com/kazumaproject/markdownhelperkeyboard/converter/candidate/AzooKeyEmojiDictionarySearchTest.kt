package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyEmojiDictionarySearchTest {
    @Test
    fun searchInputPrefixUsesNormalizedQueryAndKeepsVariations() {
        val search = AzooKeyEmojiDictionarySearch.fromAzooKeyEmojiDictionaryText(
            """
            🎂	ケーキ,たんじょうび	🥳
            🍣	すし	
            """.trimIndent()
        )

        assertEquals(
            listOf("🎂", "🥳"),
            search.searchInputPrefix("け", limit = 10).map { it.surface },
        )
        assertEquals(
            listOf("🎂", "🥳"),
            search.searchInputPrefix("タン", limit = 10).map { it.surface },
        )
    }

    @Test
    fun searchPostCommitUsesExactNormalizedQuery() {
        val search = AzooKeyEmojiDictionarySearch.fromAzooKeyEmojiDictionaryText(
            """
            🎂	ケーキ,たんじょうび	🥳
            🍣	すし	
            """.trimIndent()
        )

        assertEquals(
            listOf("🎂", "🥳"),
            search.searchPostCommit("ケーキ", limit = 10).map { it.surface },
        )
        assertEquals(
            emptyList<String>(),
            search.searchPostCommit("け", limit = 10).map { it.surface },
        )
    }

    @Test
    fun searchInputPrefixCanUseDicdataEmojiBeforeTextReplacerFallback() {
        val search = AzooKeyEmojiDictionarySearch.fromAzooKeyEmojiDictionaryTexts(
            textReplacerText = "😀️\tえがお,笑顔\t",
            dicdataText = "エガオ\t😀️\t5\t5\t501\t-20\nエガオ\t😄\t5\t5\t501\t-10",
        )

        val results = search.searchInputPrefix("えが", limit = 10)

        assertEquals(
            listOf("😄", "😀️"),
            results.map { it.surface },
        )
        assertTrue(AzooKeyDictionaryMetadata.EmojiDicdata in results.first().metadata)
        assertTrue(AzooKeyDictionaryMetadata.EmojiTextReplacer !in results.first().metadata)
    }

    @Test
    fun postCommitProviderDropsVariationEmojiLikeAzooKeyTextReplacer() = runTest {
        val textReplacer = AzooKeyTextReplacer.fromEmojiTextReplacerText(
            "🎂\tケーキ,たんじょうび\t🥳"
        )
        val provider = PostCommitEmojiDictionaryProvider(
            limit = 10,
            textReplacer = textReplacer,
        )

        assertEquals(
            listOf("🎂"),
            provider.provide(
                Candidate(
                    string = "ケーキ",
                    type = CandidateType.NBEST,
                    length = 3u,
                    score = 0,
                    data = listOf(
                        AzooKeyDictionaryEntryMapper.emoji("ケーキ", "ケーキ"),
                    ),
                ),
            ).map { it.string },
        )
    }

    @Test
    fun searchDicdataInputPrefixUsesDicdataOnly() {
        val search = AzooKeyEmojiDictionarySearch.fromAzooKeyEmojiDictionaryTexts(
            textReplacerText = "😀️\tえがお,笑顔\t",
            dicdataText = "エガオ\t😀️\t5\t5\t501\t-20\nエガオ\t😄\t5\t5\t501\t-10",
        )

        val dicdataOnly = search.searchDicdataInputPrefix("えが", limit = 10).map { it.surface }
        val mixed = search.searchInputPrefix("えが", limit = 10).map { it.surface }

        assertEquals(listOf("😄", "😀️"), dicdataOnly)
        assertTrue("😀️" in mixed)
    }
}
