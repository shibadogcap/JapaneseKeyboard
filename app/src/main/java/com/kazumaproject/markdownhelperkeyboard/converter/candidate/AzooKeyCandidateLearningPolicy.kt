package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class AzooKeyCandidateLearningPolicyInput(
    val isJapaneseMode: Boolean,
    val isLearnDictionaryMode: Boolean,
    val isPrivateMode: Boolean,
    val position: Int,
    val learnFirstCandidate: Boolean,
    val candidate: Candidate,
)

object AzooKeyCandidateLearningPolicy {
    fun shouldLearnTappedCandidate(input: AzooKeyCandidateLearningPolicyInput): Boolean {
        return input.isJapaneseMode &&
                input.isLearnDictionaryMode &&
                !input.isPrivateMode &&
                input.candidate.isLearningTarget &&
                (input.position != 0 || input.learnFirstCandidate)
    }
}
