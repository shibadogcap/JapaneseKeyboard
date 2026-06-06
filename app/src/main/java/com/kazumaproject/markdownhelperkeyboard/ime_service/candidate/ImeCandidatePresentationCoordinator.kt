package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding

/**
 * 候補結果の表示パイプラインと文節 UI 状態（Phase 2）。
 * [ImeSuggestionOrchestrator] の bunsetsu コールバックと [IMEService.setCandidates] 表示を集約する。
 */
class ImeCandidatePresentationCoordinator(
    private val host: Host,
) {
    interface Host {
        fun bunsetsuSeparationEnabled(): Boolean
        fun currentBunsetsuPositionList(): List<Int>?
        fun applyBunsetsuUiState(state: BunsetsuUiState)
        fun stringInTailLength(): Int
        fun shouldApplyCandidateResult(insertString: String, requestToken: Long): Boolean
        suspend fun updateDisplayedCandidates(insertString: String, candidates: List<Candidate>)
        suspend fun applyLiveConversion(insertString: String, candidates: List<Candidate>)
        fun updateBunsetsuSpaceKeyIfNeeded(
            mainView: MainLayoutBinding,
            candidates: List<Candidate>,
            insertString: String,
        )
        fun notifyAsyncZenzIfNeeded(insertString: String, result: ImeCandidateSuggestResult)
        fun maybeLaunchZenzRerank(
            requestToken: Long,
            insertString: String,
            baseCandidates: List<Candidate>,
            plan: ImeCandidateZenzRerankPlan,
            mainView: MainLayoutBinding,
        )
    }

    data class BunsetsuUiState(
        val splitPatterns: List<List<Int>>,
        val positionList: List<Int>,
    )

    fun filterCandidatesForTail(
        candidates: List<Candidate>,
        @Suppress("UNUSED_PARAMETER")
        insertString: String,
    ): List<Candidate> {
        return candidates
    }

    suspend fun mergeBunsetsuAfterCandidateRequest(
        input: String,
        candidates: List<Candidate>,
        bunsetsu: BunsetsuCandidateResult?,
    ) {
        host.applyBunsetsuUiState(
            computeBunsetsuUiState(
                input = input,
                mergedCandidates = candidates,
                engineResult = bunsetsu,
                bunsetsuSeparationEnabled = host.bunsetsuSeparationEnabled(),
                currentPositionList = host.currentBunsetsuPositionList(),
            ),
        )
    }

    suspend fun applySuggestionResultToView(
        insertString: String,
        mainView: MainLayoutBinding,
        result: ImeCandidateSuggestResult,
        requestToken: Long,
        notifyAsyncZenz: Boolean = true,
    ) {
        if (notifyAsyncZenz) {
            host.notifyAsyncZenzIfNeeded(insertString, result)
        }
        val filtered = filterCandidatesForTail(result.candidates, insertString)
        if (!host.shouldApplyCandidateResult(insertString, requestToken)) {
            return
        }
        host.updateDisplayedCandidates(insertString, filtered)
        host.applyLiveConversion(insertString, filtered)
        host.updateBunsetsuSpaceKeyIfNeeded(mainView, filtered, insertString)
        result.zenzRerankPlan?.let { plan ->
            host.maybeLaunchZenzRerank(requestToken, insertString, filtered, plan, mainView)
        }
    }

    companion object {
        fun sanitizeSplitPositions(
            input: String,
            splitPositions: List<Int>,
        ): List<Int> {
            return splitPositions
                .filter { it in 1 until input.length }
                .distinct()
                .sorted()
        }

        fun computeBunsetsuUiState(
            input: String,
            mergedCandidates: List<Candidate>,
            engineResult: BunsetsuCandidateResult?,
            bunsetsuSeparationEnabled: Boolean,
            currentPositionList: List<Int>?,
        ): BunsetsuUiState {
            if (!bunsetsuSeparationEnabled || engineResult == null) {
                return BunsetsuUiState(
                    splitPatterns = emptyList(),
                    positionList = emptyList(),
                )
            }
            val splitPatterns = engineResult.splitPatterns
                .map { sanitizeSplitPositions(input, it) }
                .distinct()
            val positionList = resolveInitialBunsetsuSplitPositions(
                input = input,
                mergedCandidates = mergedCandidates,
                engineResult = engineResult,
            )
            if (splitPatterns.isEmpty() && positionList.isEmpty() && currentPositionList.isNullOrEmpty()) {
                return BunsetsuUiState(emptyList(), emptyList())
            }
            return BunsetsuUiState(
                splitPatterns = splitPatterns,
                positionList = positionList,
            )
        }

        fun resolveInitialBunsetsuSplitPositions(
            input: String,
            mergedCandidates: List<Candidate>,
            engineResult: BunsetsuCandidateResult,
        ): List<Int> {
            val firstCandidate = mergedCandidates.firstOrNull() ?: return emptyList()
            if (!engineResult.candidates.contains(firstCandidate)) {
                return emptyList()
            }
            val candidatePattern = engineResult.splitPatternByCandidateString[firstCandidate.string]
                ?: if (engineResult.candidates.firstOrNull() == firstCandidate) {
                    engineResult.primarySplitPositions
                } else {
                    emptyList()
                }
            return sanitizeSplitPositions(input, candidatePattern)
        }
    }
}
