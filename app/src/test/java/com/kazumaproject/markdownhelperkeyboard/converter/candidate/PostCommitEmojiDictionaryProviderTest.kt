package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCommitEmojiDictionaryProviderTest {
    @Test
    fun provideReturnsEmptyForBlankCommittedText() = runTest {
        val provider = PostCommitEmojiDictionaryProvider(
            limit = 3,
            search = { _, _, _ -> error("search should not be called") },
        )

        assertTrue(provider.provide(" ").isEmpty())
    }

    @Test
    fun provideMapsEmojiEntriesForPostCommitPrediction() = runTest {
        val calls = mutableListOf<String>()
        val provider = PostCommitEmojiDictionaryProvider(
            limit = 3,
            search = { committedText, reading, limit ->
                calls += "$committedText:${reading ?: ""}:$limit"
                listOf(
                    AzooKeyDictionaryEntryMapper.emoji("🍣", "すし", score = -8),
                    AzooKeyDictionaryEntryMapper.symbol("※", "こめ", score = -1),
                    AzooKeyDictionaryEntryMapper.emoji("🍣", "すし", score = -9),
                    AzooKeyDictionaryEntryMapper.emoji("🍺", "びーる", score = -7),
                )
            },
        )

        val result = provider.provide("寿司")

        assertEquals(listOf("寿司::3"), calls)
        assertEquals(listOf("🍣", "🍺"), result.map { it.string })
        assertEquals(listOf(CandidateType.EMOJI_SUFFIX, CandidateType.EMOJI_SUFFIX), result.map { it.type })
        assertEquals(listOf(AzooKeyCid.SYMBOL.toShort(), AzooKeyCid.SYMBOL.toShort()), result.map { it.leftId })
        assertEquals(listOf(-8, -7), result.map { it.score })
    }

    @Test
    fun providedEmojiCandidatesCanFeedComposerAheadOfPredictions() = runTest {
        val provider = PostCommitEmojiDictionaryProvider(
            limit = 3,
            search = { _, _, _ ->
                listOf(
                    AzooKeyDictionaryEntryMapper.emoji("🎂", "けーき", score = -3),
                    AzooKeyDictionaryEntryMapper.emoji("🥳", "ぱーてぃー", score = -2),
                )
            },
        )

        val result = PostCommitPredictionComposer.compose(
            committedText = "誕生日",
            emojiCandidates = provider.provide("誕生日"),
            learnedTransitions = listOf(
                Candidate(
                    string = "おめでとう",
                    type = CandidateType.LEARNED_HISTORY,
                    length = 5.toUByte(),
                    score = 100,
                )
            ),
        )

        assertEquals(listOf("🎂", "🥳", "おめでとう"), result.map { it.string })
    }
}
