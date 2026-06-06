package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult

/**
 * AzooKey Zenzai の alternative constraint を辞書候補 / ニューラル生成へ解決する。
 */
object ZenzaiAlternativeConstraintPolicy {
    suspend fun resolveBestCandidate(
        request: ZenzPredictiveRequest,
        constraints: List<ZenzaiCandidateEvaluationResult.AlternativeConstraint>,
        generateWithPrefixContext: suspend (prefix: String) -> String,
        toZenzCandidate: (surface: String, type: Byte) -> ZenzCandidate,
    ): ZenzCandidate? {
        if (constraints.isEmpty()) return null
        return constraints.mapNotNull { constraint ->
            resolveConstraintCandidate(
                request = request,
                constraint = constraint,
                generateWithPrefixContext = generateWithPrefixContext,
                toZenzCandidate = toZenzCandidate,
            )?.let { candidate ->
                candidate to (constraint.probabilityRatio * candidate.rank(constraint.prefix))
            }
        }.maxByOrNull { it.second }
            ?.first
    }

    private suspend fun resolveConstraintCandidate(
        request: ZenzPredictiveRequest,
        constraint: ZenzaiCandidateEvaluationResult.AlternativeConstraint,
        generateWithPrefixContext: suspend (prefix: String) -> String,
        toZenzCandidate: (surface: String, type: Byte) -> ZenzCandidate,
    ): ZenzCandidate? {
        val prefix = constraint.prefix
        if (prefix.isBlank()) return null

        val dictionaryMatch = request.dictionaryCandidates
            .take(request.nBest)
            .firstOrNull { it.startsWith(prefix) }

        val engineCandidate = dictionaryMatch?.let {
            toZenzCandidate(it, CandidateType.ZENZ_CONTEXTUAL)
        }
        val generated = generateWithPrefixContext(prefix).takeIf { it.isNotBlank() }
        val neuralCandidate = generated?.let {
            toZenzCandidate(it, CandidateType.ZENZ_SPECIAL)
        }

        val candidates = listOfNotNull(neuralCandidate, engineCandidate)
        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { candidate ->
            constraint.probabilityRatio * candidate.rank(prefix)
        }
    }
}