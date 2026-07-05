package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCommitPredictionComposerTest {
    @Test
    fun composeReturnsEmptyForBlankCommittedText() {
        val result = PostCommitPredictionComposer.compose(
            committedText = " ",
            learnedTransitions = listOf(candidate("候補", score = 10))
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun composeSortsLearnedTransitionsWithoutSyntheticParticles() {
        val result = PostCommitPredictionComposer.compose(
            committedText = "今日は",
            learnedTransitions = listOf(
                candidate("晴れ", score = 200),
                candidate("雨", score = 100)
            )
        )

        assertEquals(listOf("雨", "晴れ"), result.map { it.string })
        assertTrue(result.all { it.type == CandidateType.LEARNED_HISTORY })
    }

    @Test
    fun composeDropsSelfBlankAndDuplicateCandidates() {
        val result = PostCommitPredictionComposer.compose(
            committedText = "東京",
            learnedTransitions = listOf(
                candidate("東京", score = 100),
                candidate("", score = 200),
                candidate("駅", score = 300)
            ),
            zeroHintCandidates = listOf(zeroHint("駅", score = 90), zeroHint("で", score = 100))
        )

        assertEquals(listOf("駅", "で"), result.map { it.string })
    }

    @Test
    fun composeRespectsLimit() {
        val result = PostCommitPredictionComposer.compose(
            committedText = "明日",
            learnedTransitions = listOf(
                candidate("行く", score = 150),
                candidate("見る", score = 70)
            ),
            zeroHintCandidates = listOf(zeroHint("は", score = 80), zeroHint("に", score = 90)),
            limit = 3
        )

        assertEquals(listOf("見る", "は", "に"), result.map { it.string })
    }

    @Test
    fun composeDoesNotCreateSyntheticParticleFallbacks() {
        val result = PostCommitPredictionComposer.compose(
            committedText = "東京",
            learnedTransitions = emptyList(),
            limit = 10,
            particleLimit = 3
        )

        assertEquals(emptyList<String>(), result.map { it.string })
    }

    @Test
    fun composeLimitsDictionaryZeroHintParticles() {
        val result = PostCommitPredictionComposer.compose(
            committedText = "東京",
            learnedTransitions = emptyList(),
            zeroHintCandidates = listOf(
                zeroHint("は", score = 60),
                zeroHint("が", score = 70),
                zeroHint("の", score = 80),
                zeroHint("に", score = 90),
                zeroHint("駅", score = 100),
            ),
            limit = 10,
            particleLimit = 3
        )

        assertEquals(listOf("は", "が", "の", "駅"), result.map { it.string })
    }

    @Test
    fun composeUsesAzooKeyStyleEmojiPredictionZeroHintBudget() {
        val result = PostCommitPredictionComposer.compose(
            committedText = "猫",
            emojiCandidates = listOf(
                emoji("🐈", score = -3),
                emoji("😺", score = -4),
                emoji("🐱", score = -2),
                emoji("🐾", score = -5)
            ),
            learnedTransitions = listOf(
                candidate("かわいい", score = 50),
                candidate("です", score = 60),
                candidate("が", score = 70),
                candidate("を", score = 80),
                candidate("と", score = 90)
            ),
            zeroHintCandidates = listOf(
                zeroHint("。", score = 700),
                zeroHint("！", score = 800),
                zeroHint("？", score = 900),
                zeroHint("です", score = 1000)
            ),
            particleLimit = 0,
            limit = 10
        )

        assertEquals(
            listOf("😺", "🐱", "🐾", "かわいい", "です", "が", "。", "！", "？"),
            result.map { it.string }
        )
    }

    private fun candidate(
        string: String,
        score: Int
    ): Candidate {
        return Candidate(
            string = string,
            type = CandidateType.LEARNED_HISTORY,
            length = string.length.toUByte(),
            score = score
        )
    }

    private fun emoji(
        string: String,
        score: Int
    ): Candidate {
        return Candidate(
            string = string,
            type = CandidateType.EMOJI_SPECIAL,
            length = string.length.toUByte(),
            score = score
        )
    }

    private fun zeroHint(
        string: String,
        score: Int,
    ): Candidate {
        return Candidate(
            string = string,
            type = CandidateType.ZERO_HINT_PREDICTION,
            length = string.length.toUByte(),
            score = score,
            value = -score.toFloat(),
        )
    }
}
