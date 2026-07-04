package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryMetadata
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult

internal sealed interface AzooKeyZenzaiNextAction {
    data class ReturnResult(
        val constraint: AzooKeyPrefixConstraint,
        val alternativeConstraints: List<ZenzaiCandidateEvaluationResult.AlternativeConstraint>,
        val satisfied: Boolean,
    ) : AzooKeyZenzaiNextAction

    data object ContinueLoop : AzooKeyZenzaiNextAction

    data class Retry(val candidateIndex: Int) : AzooKeyZenzaiNextAction
}

internal object AzooKeyZenzaiReview {
    fun review(
        kana2Kanji: AzooKeyKana2Kanji,
        candidateIndex: Int,
        candidates: List<Candidate>,
        reviewResult: ZenzaiCandidateEvaluationResult,
        constraint: AzooKeyPrefixConstraint,
    ): Pair<AzooKeyZenzaiNextAction, AzooKeyPrefixConstraint> {
        var mutableConstraint = constraint
        val action = when (reviewResult) {
            ZenzaiCandidateEvaluationResult.Error ->
                AzooKeyZenzaiNextAction.ReturnResult(mutableConstraint, emptyList(), satisfied = false)

            is ZenzaiCandidateEvaluationResult.Pass ->
                AzooKeyZenzaiNextAction.ReturnResult(
                    constraint = mutableConstraint,
                    alternativeConstraints = reviewResult.alternativeConstraints,
                    satisfied = true,
                )

            is ZenzaiCandidateEvaluationResult.FixRequired -> {
                val newConstraint = AzooKeyPrefixConstraint.normalized(
                    constraintBytes = reviewResult.prefix.toByteArray(Charsets.UTF_8),
                    defaultHasEos = false,
                    ignoreMemoryAndUserDictionary = mutableConstraint.ignoreMemoryAndUserDictionary,
                )
                handleConstraintUpdate(
                    kana2Kanji = kana2Kanji,
                    candidateIndex = candidateIndex,
                    candidates = candidates,
                    previousConstraint = mutableConstraint,
                    newConstraint = newConstraint,
                    isWholeResult = false,
                ).also { (next, updated) ->
                    mutableConstraint = updated
                }.first
            }

            is ZenzaiCandidateEvaluationResult.WholeResult -> {
                val newConstraint = AzooKeyPrefixConstraint.normalized(
                    constraintBytes = reviewResult.result.toByteArray(Charsets.UTF_8),
                    defaultHasEos = true,
                    ignoreMemoryAndUserDictionary = mutableConstraint.ignoreMemoryAndUserDictionary,
                )
                handleConstraintUpdate(
                    kana2Kanji = kana2Kanji,
                    candidateIndex = candidateIndex,
                    candidates = candidates,
                    previousConstraint = mutableConstraint,
                    newConstraint = newConstraint,
                    isWholeResult = true,
                ).also { (next, updated) ->
                    mutableConstraint = updated
                }.first
            }
        }
        return action to mutableConstraint
    }

    private fun handleConstraintUpdate(
        kana2Kanji: AzooKeyKana2Kanji,
        candidateIndex: Int,
        candidates: List<Candidate>,
        previousConstraint: AzooKeyPrefixConstraint,
        newConstraint: AzooKeyPrefixConstraint,
        isWholeResult: Boolean,
    ): Pair<AzooKeyZenzaiNextAction, AzooKeyPrefixConstraint> {
        if (previousConstraint == newConstraint) {
            val candidate = candidates[candidateIndex]
            val hasLearnedOrUser = candidate.data.any {
                AzooKeyDictionaryMetadata.Learned in it.metadata ||
                    AzooKeyDictionaryMetadata.FromUserDictionary in it.metadata
            }
            if (!previousConstraint.ignoreMemoryAndUserDictionary && hasLearnedOrUser) {
                val retryConstraint = previousConstraint.copy(ignoreMemoryAndUserDictionary = true)
                findRetryIndex(kana2Kanji, candidates, candidateIndex, newConstraint)?.let { index ->
                    return AzooKeyZenzaiNextAction.Retry(index) to retryConstraint
                }
                return AzooKeyZenzaiNextAction.ContinueLoop to retryConstraint
            }
            return AzooKeyZenzaiNextAction.ReturnResult(
                constraint = AzooKeyPrefixConstraint(),
                alternativeConstraints = emptyList(),
                satisfied = false,
            ) to previousConstraint
        }

        val isIncrementalUpdate = newConstraint.constraint.size >= previousConstraint.constraint.size &&
            previousConstraint.constraint.indices.all { newConstraint.constraint[it] == previousConstraint.constraint[it] }
        var updatedConstraint = newConstraint
        if (isIncrementalUpdate) {
            findRetryIndex(kana2Kanji, candidates, candidateIndex, newConstraint)?.let { index ->
                return AzooKeyZenzaiNextAction.Retry(index) to updatedConstraint
            }
        }
        return AzooKeyZenzaiNextAction.ContinueLoop to updatedConstraint
    }

    private fun findRetryIndex(
        kana2Kanji: AzooKeyKana2Kanji,
        candidates: List<Candidate>,
        skipIndex: Int,
        constraint: AzooKeyPrefixConstraint,
    ): Int? {
        return candidates.indices.firstOrNull { index ->
            index != skipIndex &&
                kana2Kanji.candidateSatisfies(candidates[index], constraint) &&
                heuristicRetryValidation(candidates[index].string)
        }
    }

    private fun heuristicRetryValidation(text: String): Boolean {
        if ('\u3099' in text || '\u309A' in text) return false
        return true
    }
}
