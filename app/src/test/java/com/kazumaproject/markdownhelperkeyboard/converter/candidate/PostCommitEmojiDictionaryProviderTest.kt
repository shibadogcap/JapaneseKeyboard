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
            fallbackSearch = { _, _, _ -> error("search should not be called") },
        )

        assertTrue(provider.provide(leftSideCandidate(" ")).isEmpty())
    }

    @Test
    fun provideUsesTextReplacerForCandidateDataWords() = runTest {
        val textReplacer = AzooKeyTextReplacer.fromEmojiTextReplacerText(
            """
            🎂	ケーキ,たんじょうび	🥳
            🍣	すし	
            """.trimIndent()
        )
        val provider = PostCommitEmojiDictionaryProvider(
            limit = 3,
            textReplacer = textReplacer,
        )

        val result = provider.provide(
            leftSideCandidate(
                string = "誕生日",
                data = listOf(
                    dictionaryEntry(surface = "ケーキ", reading = "ケーキ"),
                ),
            ),
        )

        assertEquals(listOf("🎂"), result.map { it.string })
        assertEquals(listOf(CandidateType.EMOJI_SUFFIX), result.map { it.type })
    }

    @Test
    fun provideMapsFallbackEmojiEntriesForPostCommitPrediction() = runTest {
        val calls = mutableListOf<String>()
        val provider = PostCommitEmojiDictionaryProvider(
            limit = 3,
            fallbackSearch = { committedText, reading, limit ->
                calls += "$committedText:${reading ?: ""}:$limit"
                listOf(
                    AzooKeyDictionaryEntryMapper.emoji("🍣", "すし", score = -8),
                    AzooKeyDictionaryEntryMapper.symbol("※", "こめ", score = -1),
                    AzooKeyDictionaryEntryMapper.emoji("🍣", "すし", score = -9),
                    AzooKeyDictionaryEntryMapper.emoji("🍺", "びーる", score = -7),
                )
            },
        )

        val result = provider.provide(leftSideCandidate("寿司"))

        assertEquals(listOf("寿司::3"), calls)
        assertEquals(listOf("🍣", "🍺"), result.map { it.string })
        assertEquals(listOf(CandidateType.EMOJI_SUFFIX, CandidateType.EMOJI_SUFFIX), result.map { it.type })
    }

    @Test
    fun providedEmojiCandidatesCanFeedComposerAheadOfPredictions() = runTest {
        val textReplacer = AzooKeyTextReplacer.fromEmojiTextReplacerText(
            "🎂\tケーキ,たんじょうび\t🥳"
        )
        val provider = PostCommitEmojiDictionaryProvider(
            limit = 3,
            textReplacer = textReplacer,
        )

        val result = PostCommitPredictionComposer.compose(
            committedText = "誕生日",
            emojiCandidates = provider.provide(
                leftSideCandidate(
                    string = "誕生日",
                    data = listOf(dictionaryEntry(surface = "ケーキ", reading = "ケーキ")),
                ),
            ),
            learnedTransitions = listOf(
                Candidate(
                    string = "おめでとう",
                    type = CandidateType.LEARNED_HISTORY,
                    length = 5.toUByte(),
                    score = 100,
                )
            ),
        )

        assertEquals(listOf("🎂", "おめでとう"), result.map { it.string })
    }

    private fun leftSideCandidate(
        string: String,
        data: List<AzooKeyDictionaryEntry> = listOf(dictionaryEntry(surface = string, reading = string)),
    ): Candidate {
        return Candidate(
            string = string,
            type = CandidateType.NBEST,
            length = string.length.toUByte(),
            score = 0,
            yomi = data.firstOrNull()?.reading,
            data = data,
        )
    }

    private fun dictionaryEntry(
        surface: String,
        reading: String,
    ): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = AzooKeyCid.GENERAL_NOUN,
            rightId = AzooKeyCid.GENERAL_NOUN,
            mid = AzooKeyMid.GENERAL,
            wordCost = 0,
            sourceKind = AzooKeyDictionarySourceKind.System,
        )
    }
}
