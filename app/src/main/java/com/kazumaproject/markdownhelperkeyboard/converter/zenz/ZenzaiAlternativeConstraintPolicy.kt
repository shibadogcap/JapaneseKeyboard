package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult

/**
 * AzooKey Zenzai の alternative constraint を辞書候補 / ニューラル生成へ解決する。
 */
object ZenzaiAlternativeConstraintPolicy {
    fun resolveBestCandidate(
        request: ZenzPredictiveRequest,
        constraints: List<ZenzaiCandidateEvaluationResult.AlternativeConstraint>,
        toZenzCandidate: (surface: String, type: Byte) -> ZenzCandidate,
    ): ZenzCandidate? {
        if (constraints.isEmpty()) return null
        return constraints.mapNotNull { constraint ->
            resolveConstraintCandidate(
                request = request,
                constraint = constraint,
                toZenzCandidate = toZenzCandidate,
            )?.let { candidate ->
                candidate to (constraint.probabilityRatio * candidate.rank(constraint.prefix))
            }
        }.maxByOrNull { it.second }
            ?.first
    }

    private fun resolveConstraintCandidate(
        request: ZenzPredictiveRequest,
        constraint: ZenzaiCandidateEvaluationResult.AlternativeConstraint,
        toZenzCandidate: (surface: String, type: Byte) -> ZenzCandidate,
    ): ZenzCandidate? {
        val prefix = constraint.prefix
        if (prefix.isBlank()) return null

        val dictionaryMatch = request.dictionaryCandidates
            .take(request.nBest)
            .firstOrNull { it.string.startsWith(prefix) }

        val engineCandidate = dictionaryMatch?.let { match ->
            val finalType = if (isSpecialType(match.type)) match.type else CandidateType.ZENZ_CONTEXTUAL
            toZenzCandidate(match.string, finalType)
        }

        return engineCandidate
    }

    private fun isSpecialType(type: Byte): Boolean {
        return type in listOf(
            CandidateType.TYPOGRAPHY_SPECIAL,
            CandidateType.UNICODE_SPECIAL,
            CandidateType.EMOJI_SPECIAL,
            CandidateType.SYMBOL_SPECIAL,
            CandidateType.EMAIL_ADDRESS_SPECIAL,
            CandidateType.CALENDAR_SPECIAL,
            CandidateType.TIME_EXPRESSION_SPECIAL,
            CandidateType.VERSION_SPECIAL,
            CandidateType.COMMA_SEPARATED_NUMBER_SPECIAL,
            CandidateType.HALF_WIDTH_KATAKANA_SPECIAL,
            CandidateType.EMOJI_LEGACY,
            CandidateType.EMOTICON_LEGACY,
            CandidateType.SYMBOL_LEGACY
        )
    }
}