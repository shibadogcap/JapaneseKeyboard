package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class LearnedTransitionCandidateMapperTest {
    @Test
    fun mapsLearnedTransitionRecordToLearnedHistoryCandidate() {
        val candidate = LearnedTransitionCandidateMapper.toCandidate(
            LearnedTransitionRecord(
                input = "東京",
                output = "駅",
                score = 3000,
                leftId = 1,
                rightId = 2,
            )
        )

        assertEquals("駅", candidate.string)
        assertEquals("東京", candidate.yomi)
        assertEquals(CandidateType.LEARNED_HISTORY, candidate.type)
        assertEquals(2.toUByte(), candidate.length)
        assertEquals(3000, candidate.score)
        assertEquals(
            AzooKeyPValues.legacyLearnScoreToLearningMemoryValue(
                rubyLength = 2,
                legacyScore = 3000,
            ),
            candidate.value,
        )
        assertEquals(1.toShort(), candidate.leftId)
        assertEquals(2.toShort(), candidate.rightId)
    }
}
