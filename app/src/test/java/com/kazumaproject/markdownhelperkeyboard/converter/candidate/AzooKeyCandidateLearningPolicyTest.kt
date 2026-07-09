package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyCandidateLearningPolicyTest {
    @Test
    fun learnsNonFirstJapaneseCandidateWhenLearningIsEnabled() {
        assertTrue(policy(position = 1))
    }

    @Test
    fun blocksFirstCandidateUnlessPreferenceAllowsIt() {
        assertFalse(policy(position = 0, learnFirstCandidate = false))
        assertTrue(policy(position = 0, learnFirstCandidate = true))
    }

    @Test
    fun blocksWhenLearningWritesAreDisabled() {
        assertFalse(policy(position = 1, allowsLearningWrites = false))
    }

    @Test
    fun blocksNonJapaneseMode() {
        assertFalse(policy(position = 1, isJapaneseMode = false))
    }

    @Test
    fun blocksCandidateThatIsNotLearningTarget() {
        assertFalse(policy(position = 1, candidate = candidate(isLearningTarget = false)))
    }

    private fun policy(
        isJapaneseMode: Boolean = true,
        isLearnDictionaryMode: Boolean = true,
        allowsLearningWrites: Boolean = true,
        position: Int,
        learnFirstCandidate: Boolean = false,
        candidate: Candidate = candidate(),
    ): Boolean {
        return AzooKeyCandidateLearningPolicy.shouldLearnTappedCandidate(
            AzooKeyCandidateLearningPolicyInput(
                isJapaneseMode = isJapaneseMode,
                isLearnDictionaryMode = isLearnDictionaryMode,
                allowsLearningWrites = allowsLearningWrites,
                position = position,
                learnFirstCandidate = learnFirstCandidate,
                candidate = candidate,
            )
        )
    }

    private fun candidate(isLearningTarget: Boolean = true): Candidate {
        return Candidate(
            string = "候補",
            type = CandidateType.NBEST,
            length = 2.toUByte(),
            score = 100,
            isLearningTarget = isLearningTarget,
        )
    }
}
