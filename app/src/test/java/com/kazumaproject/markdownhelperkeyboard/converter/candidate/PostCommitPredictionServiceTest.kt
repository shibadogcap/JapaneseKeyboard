package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCommitPredictionServiceTest {
    @Test
    fun predictCombinesEmojiAndLearnedTransitions() = runTest {
        val service = service(
            learned = listOf(candidate("駅", score = 300), candidate("です", score = 100)),
            emojiEntries = listOf(emojiEntry("🗼", "東京")),
        )

        val result = service.predict(
            leftSideCandidate = candidate("東京", score = 100),
            useLearnedTransitions = true,
        )

        assertEquals(listOf("🗼", "駅", "です"), result.map { it.string })
        assertEquals(CandidateType.EMOJI_SUFFIX, result.first().type)
    }

    @Test
    fun predictSkipsLearnedTransitionsWhenDisabled() = runTest {
        val service = service(
            learned = listOf(candidate("駅", score = 300)),
            emojiEntries = listOf(emojiEntry("🗼", "東京")),
        )

        val result = service.predict(
            leftSideCandidate = candidate("東京", score = 100),
            useLearnedTransitions = false,
        )

        assertEquals(listOf("🗼"), result.map { it.string })
    }

    @Test
    fun predictMergesLoudsTransitionsWithLearnedByValue() = runTest {
        val service = PostCommitPredictionService(
            searchLearnedTransitions = { _, _ ->
                listOf(candidate("です", score = 100, value = 10f))
            },
            searchLoudsTransitions = { _, _ ->
                listOf(candidate("駅", score = 300, value = 50f))
            },
            emojiProvider = PostCommitEmojiDictionaryProvider(
                limit = 8,
                fallbackSearch = { _, _, _ -> emptyList() },
            ),
        )

        val result = service.predict(
            leftSideCandidate = candidate("東京", score = 100),
            useLearnedTransitions = true,
        )

        assertEquals("駅", result.first().string)
        assertTrue(result.any { it.string == "です" })
    }

    @Test
    fun predictDoesNotUseSyntheticFallbackPredictionsWhenNoLearnedOrLoudsTransitionExists() = runTest {
        val service = PostCommitPredictionService(
            searchLearnedTransitions = { _, _ -> emptyList() },
            searchLoudsTransitions = { _, _ -> emptyList() },
            emojiProvider = PostCommitEmojiDictionaryProvider(
                limit = 8,
                fallbackSearch = { _, _, _ -> emptyList() },
            ),
        )

        val result = service.predict(
            leftSideCandidate = candidate("確認", score = 100),
            useLearnedTransitions = true,
        )

        assertEquals(emptyList<String>(), result.map { it.string })
    }

    @Test
    fun predictUsesYomiForLoudsTransitionLookup() = runTest {
        var loudsQuery: String? = null
        val service = PostCommitPredictionService(
            searchLearnedTransitions = { _, _ -> emptyList() },
            searchLoudsTransitions = { reading, _ ->
                loudsQuery = reading
                emptyList()
            },
            emojiProvider = PostCommitEmojiDictionaryProvider(
                limit = 8,
                fallbackSearch = { _, _, _ -> emptyList() },
            ),
        )

        service.predict(
            leftSideCandidate = Candidate(
                string = "東京",
                yomi = "とうきょう",
                type = CandidateType.NBEST,
                length = 3.toUByte(),
                score = 0,
            ),
            useLearnedTransitions = false,
        )

        assertEquals("とうきょう", loudsQuery)
    }

    @Test
    fun predictSkipsZeroHintWithoutYomi() = runTest {
        var zeroHintCalled = false
        val service = PostCommitPredictionService(
            searchLearnedTransitions = { _, _ -> emptyList() },
            searchZeroHintCandidates = { _, _ ->
                zeroHintCalled = true
                emptyList()
            },
            emojiProvider = PostCommitEmojiDictionaryProvider(
                limit = 8,
                fallbackSearch = { _, _, _ -> emptyList() },
            ),
        )

        service.predict(
            leftSideCandidate = Candidate(
                string = "東京",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 0,
            ),
            useLearnedTransitions = false,
        )

        assertEquals(false, zeroHintCalled)
    }

    @Test
    fun predictUsesYomiForZeroHintLookup() = runTest {
        var zeroHintQuery: String? = null
        val service = PostCommitPredictionService(
            searchLearnedTransitions = { _, _ -> emptyList() },
            searchZeroHintCandidates = { leftSide, _ ->
                zeroHintQuery = leftSide.yomi ?: leftSide.string
                listOf(candidate("駅", score = 200, value = 20f))
            },
            emojiProvider = PostCommitEmojiDictionaryProvider(
                limit = 8,
                fallbackSearch = { _, _, _ -> emptyList() },
            ),
        )

        service.predict(
            leftSideCandidate = Candidate(
                string = "東京",
                yomi = "とうきょう",
                type = CandidateType.NBEST,
                length = 3.toUByte(),
                score = 0,
            ),
            useLearnedTransitions = false,
        )

        assertEquals("とうきょう", zeroHintQuery)
    }

    @Test
    fun predictDoesNotSearchSourcesForBlankText() = runTest {
        var learnedSearchCount = 0
        var emojiSearchCount = 0
        val service = PostCommitPredictionService(
            searchLearnedTransitions = { _, _ ->
                learnedSearchCount += 1
                emptyList()
            },
            emojiProvider = PostCommitEmojiDictionaryProvider(
                limit = 8,
                fallbackSearch = { _, _, _ ->
                    emojiSearchCount += 1
                    emptyList()
                }
            ),
        )

        assertEquals(
            emptyList<Candidate>(),
            service.predict(candidate(" ", score = 0), useLearnedTransitions = true),
        )
        assertEquals(0, learnedSearchCount)
        assertEquals(0, emojiSearchCount)
    }

    private fun service(
        learned: List<Candidate>,
        emojiEntries: List<AzooKeyDictionaryEntry>,
    ): PostCommitPredictionService {
        return PostCommitPredictionService(
            searchLearnedTransitions = { _, _ -> learned },
            emojiProvider = PostCommitEmojiDictionaryProvider(
                limit = 8,
                fallbackSearch = { _, _, _ -> emojiEntries },
            ),
        )
    }

    private fun candidate(string: String, score: Int, value: Float = score.toFloat()): Candidate {
        return Candidate(
            string = string,
            type = CandidateType.LEARNED_HISTORY,
            length = string.length.toUByte(),
            score = score,
            value = value,
        )
    }

    private fun emojiEntry(surface: String, reading: String): AzooKeyDictionaryEntry {
        return AzooKeyDictionaryEntry(
            surface = surface,
            reading = reading,
            leftId = AzooKeyCid.SYMBOL,
            rightId = AzooKeyCid.SYMBOL,
            mid = AzooKeyMid.GENERAL,
            wordCost = -3,
            sourceKind = AzooKeyDictionarySourceKind.Emoji,
        )
    }
}
