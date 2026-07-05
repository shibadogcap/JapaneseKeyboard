package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyPostCommitPredictionPolicyTest {
    @Test
    fun requestsPredictionForJapaneseCommittedWord() {
        assertTrue(shouldRequest(committedText = "東京"))
    }

    @Test
    fun blocksBlankPrivateSuppressedAndNonJapaneseInput() {
        assertFalse(shouldRequest(committedText = " "))
        assertFalse(shouldRequest(committedText = "東京", isPrivateMode = true))
        assertFalse(shouldRequest(committedText = "東京", suppressSuggestions = true))
        assertFalse(shouldRequest(committedText = "東京", isJapaneseMode = false))
    }

    @Test
    fun blocksTerminalPunctuationLikeAzooKeyPostCompositionCandidate() {
        assertFalse(shouldRequest(committedText = "。"))
        assertFalse(shouldRequest(committedText = "."))
        assertFalse(shouldRequest(committedText = "．"))
    }

    @Test
    fun nextWordTransitionReadingPrefersYomi() {
        val candidate = Candidate(
            string = "東京",
            yomi = "とうきょう",
            type = CandidateType.NBEST,
            length = 2.toUByte(),
            score = 0,
        )
        assertEquals(
            "とうきょう",
            AzooKeyPostCommitPredictionPolicy.nextWordTransitionReading(candidate),
        )
    }

    @Test
    fun zeroHintRequiresYomi() {
        val withYomi = Candidate(
            string = "東京",
            yomi = "とうきょう",
            type = CandidateType.NBEST,
            length = 2.toUByte(),
            score = 0,
        )
        val surfaceOnly = Candidate(
            string = "東京",
            type = CandidateType.NBEST,
            length = 2.toUByte(),
            score = 0,
        )
        assertTrue(AzooKeyPostCommitPredictionPolicy.shouldSearchZeroHint(withYomi))
        assertFalse(AzooKeyPostCommitPredictionPolicy.shouldSearchZeroHint(surfaceOnly))
    }

    @Test
    fun learnedTransitionsWithFallbackRequiresBothFlagsWhenSnapshotNull() {
        assertFalse(
            AzooKeyPostCommitPredictionPolicy.shouldUseLearnedTransitionsWithFallback(
                isLearnDictionaryMode = false,
                predictionLearnSearchEnabled = true,
            ),
        )
        assertFalse(
            AzooKeyPostCommitPredictionPolicy.shouldUseLearnedTransitionsWithFallback(
                isLearnDictionaryMode = true,
                predictionLearnSearchEnabled = false,
            ),
        )
        assertTrue(
            AzooKeyPostCommitPredictionPolicy.shouldUseLearnedTransitionsWithFallback(
                isLearnDictionaryMode = true,
                predictionLearnSearchEnabled = true,
            ),
        )
    }

    @Test
    fun learnedTransitionsRequireLearnModeAndPredictionSearch() {
        assertTrue(
            AzooKeyPostCommitPredictionPolicy.shouldUseLearnedTransitions(
                isLearnDictionaryMode = true,
                predictionLearnSearchEnabled = true,
            ),
        )
        assertFalse(
            AzooKeyPostCommitPredictionPolicy.shouldUseLearnedTransitions(
                isLearnDictionaryMode = false,
                predictionLearnSearchEnabled = true,
            ),
        )
    }

    private fun shouldRequest(
        committedText: String,
        isPrivateMode: Boolean = false,
        suppressSuggestions: Boolean = false,
        isJapaneseMode: Boolean = true,
    ): Boolean {
        return AzooKeyPostCommitPredictionPolicy.shouldRequestPrediction(
            AzooKeyPostCommitPredictionPolicyInput(
                committedText = committedText,
                isPrivateMode = isPrivateMode,
                suppressSuggestions = suppressSuggestions,
                isJapaneseMode = isJapaneseMode,
            )
        )
    }
}
