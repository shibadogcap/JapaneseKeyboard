package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyTransitionLearningPolicyTest {
    @Test
    fun learnsTransitionBetweenDifferentCommittedWords() {
        assertTrue(shouldLearn(previousCommittedText = "東京", committedText = "駅"))
    }

    @Test
    fun blocksBlankSameAndDisabledLearning() {
        assertFalse(shouldLearn(previousCommittedText = null, committedText = "駅"))
        assertFalse(shouldLearn(previousCommittedText = "東京", committedText = " "))
        assertFalse(shouldLearn(previousCommittedText = "東京", committedText = "東京"))
        assertFalse(shouldLearn(previousCommittedText = "東京", committedText = "駅", allowsLearningWrites = false))
        assertFalse(shouldLearn(previousCommittedText = "東京", committedText = "駅", isLearnDictionaryMode = false))
    }

    @Test
    fun blocksCandidateThatIsNotLearningTarget() {
        assertFalse(
            shouldLearn(
                previousCommittedText = "バージョン",
                committedText = "JapaneseKeyboard Version 1.0",
                candidate = candidate(isLearningTarget = false),
            )
        )
    }

    private fun shouldLearn(
        previousCommittedText: String?,
        committedText: String,
        isLearnDictionaryMode: Boolean = true,
        allowsLearningWrites: Boolean = true,
        candidate: Candidate? = candidate(),
    ): Boolean {
        return AzooKeyTransitionLearningPolicy.shouldLearnTransition(
            AzooKeyTransitionLearningPolicyInput(
                previousCommittedText = previousCommittedText,
                committedText = committedText,
                isLearnDictionaryMode = isLearnDictionaryMode,
                allowsLearningWrites = allowsLearningWrites,
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
