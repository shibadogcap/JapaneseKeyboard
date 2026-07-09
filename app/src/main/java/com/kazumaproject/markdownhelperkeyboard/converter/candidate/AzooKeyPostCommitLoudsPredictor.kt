package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyDicdataFacade
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyDicdataStoreUtils

/**
 * AzooKey [getPredictionCandidates] / [getZeroHintPredictionCandidates] 相当の LOUDS 予測（確定後）。
 */
internal object AzooKeyPostCommitLoudsPredictor {
    suspend fun predictTransitions(
        leftSideCandidate: Candidate,
        facade: AzooKeyDicdataFacade,
        useMemory: Boolean,
        limit: Int,
    ): List<Candidate> {
        val transitionReading = AzooKeyPostCommitPredictionPolicy.nextWordTransitionReading(leftSideCandidate)
            ?.hiraganaToKatakana()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()
        return scoreEntries(
            leftSideCandidate = leftSideCandidate,
            facade = facade,
            entries = facade.getPredictionLOUDSDicdata(
                key = transitionReading,
                useMemory = useMemory,
                includeExactMatch = false,
            ),
            transitionReadingLength = transitionReading.length,
            type = CandidateType.POST_COMMIT_PREDICTION,
            limit = limit,
        )
    }

    suspend fun predictZeroHint(
        leftSideCandidate: Candidate,
        facade: AzooKeyDicdataFacade,
        useMemory: Boolean,
        limit: Int,
    ): List<Candidate> {
        if (!AzooKeyPostCommitPredictionPolicy.shouldSearchZeroHint(leftSideCandidate)) {
            return emptyList()
        }
        val lastReading = leftSideCandidate.data.lastOrNull()?.reading
            ?: leftSideCandidate.yomi?.hiraganaToKatakana()
            ?: return emptyList()
        if (lastReading.isEmpty()) {
            return emptyList()
        }
        return scoreEntries(
            leftSideCandidate = leftSideCandidate,
            facade = facade,
            entries = facade.getPredictionLOUDSDicdata(
                key = lastReading,
                useMemory = useMemory,
                includeExactMatch = true,
            ),
            transitionReadingLength = lastReading.length,
            type = CandidateType.ZERO_HINT_PREDICTION,
            limit = limit,
        ).filter { candidate ->
            candidate.string != leftSideCandidate.string &&
                !leftSideCandidate.string.endsWith(candidate.string)
        }
    }

    private fun scoreEntries(
        leftSideCandidate: Candidate,
        facade: AzooKeyDicdataFacade,
        entries: List<AzooKeyDictionaryEntry>,
        transitionReadingLength: Int,
        type: Byte,
        limit: Int,
    ): List<Candidate> {
        if (entries.isEmpty() || limit <= 0) {
            return emptyList()
        }
        val lastRcid = leftSideCandidate.data.lastOrNull()?.rightId ?: AzooKeyCid.BOS
        val lastMid = leftSideCandidate.lastMid
        val ccLatter = facade.getCCLatter(lastRcid)
        val baseValue = leftSideCandidate.value

        return entries
            .asSequence()
            .map { entry ->
                val includeMm = AzooKeyDicdataStoreUtils.includeMMValueCalculation(entry.leftId, entry.rightId)
                val mmValue = if (includeMm) facade.getMMValue(lastMid, entry.mid) else 0f
                val ccValue = ccLatter.getOrNull(entry.leftId ?: AzooKeyCid.PROPER_NOUN)
                    ?: facade.getConnectionCost(lastRcid, entry.leftId ?: AzooKeyCid.PROPER_NOUN)
                val penalty = -(entry.reading.length - transitionReadingLength).toFloat()
                val value = baseValue + mmValue + ccValue + entry.value + penalty
                Candidate(
                    string = entry.surface,
                    type = type,
                    length = entry.surface.length.toUByte(),
                    score = value.toInt(),
                    value = value,
                    yomi = entry.reading,
                    leftId = entry.leftId?.toShort(),
                    rightId = entry.rightId?.toShort(),
                    data = listOf(entry),
                    lastMid = entry.mid,
                )
            }
            .sortedByDescending { it.value }
            .take(limit)
            .toList()
    }
}
