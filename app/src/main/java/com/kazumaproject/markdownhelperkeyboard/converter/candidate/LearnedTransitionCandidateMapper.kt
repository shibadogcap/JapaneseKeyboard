package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class LearnedTransitionRecord(
    val input: String,
    val output: String,
    val score: Int,
    val value: AzooKeyPValue = AzooKeyPValues.legacyLearnScoreToLearningMemoryValue(
        rubyLength = input.length,
        legacyScore = score,
    ),
    val leftId: Short? = null,
    val rightId: Short? = null,
)

object LearnedTransitionCandidateMapper {
    fun toCandidate(record: LearnedTransitionRecord): Candidate {
        return Candidate(
            string = record.output,
            type = CandidateType.LEARNED_HISTORY,
            length = record.input.length.toUByte(),
            score = record.score,
            value = record.value,
            yomi = record.input,
            leftId = record.leftId,
            rightId = record.rightId,
        )
    }
}
