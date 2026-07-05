package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class ImeCandidatePresentationCoordinatorTest {
    @Test
    fun sanitizeSplitPositionsFiltersOutOfRange() {
        val result = ImeCandidatePresentationCoordinator.sanitizeSplitPositions(
            input = "あいう",
            splitPositions = listOf(0, 1, 3, 99),
        )
        assertEquals(listOf(1), result)
    }

    @Test
    fun computeBunsetsuUiStateClearsWhenSeparationDisabled() {
        val state = ImeCandidatePresentationCoordinator.computeBunsetsuUiState(
            input = "かんじ",
            mergedCandidates = emptyList(),
            engineResult = mock(),
            bunsetsuSeparationEnabled = false,
            currentPositionList = listOf(2),
        )
        assertTrue(state.splitPatterns.isEmpty())
        assertTrue(state.positionList.isEmpty())
    }

    @Test
    fun filterCandidatesForTailKeepsHeadCandidatesWhenTailExists() {
        var tailLen = 2
        val coordinator = ImeCandidatePresentationCoordinator(
            object : ImeCandidatePresentationCoordinator.Host {
                override fun bunsetsuSeparationEnabled() = false
                override fun currentBunsetsuPositionList(): List<Int>? = null
                override fun applyBunsetsuUiState(state: ImeCandidatePresentationCoordinator.BunsetsuUiState) {}
                override fun stringInTailLength() = tailLen
                override fun shouldApplyCandidateResult(insertString: String, requestToken: Long) = true
                override suspend fun updateDisplayedCandidates(
                    insertString: String,
                    candidates: List<Candidate>,
                ) {}
                override suspend fun applyLiveConversion(
                    insertString: String,
                    candidates: List<Candidate>,
                    firstClauseResults: List<Candidate>,
                ) {}
                override fun updateBunsetsuSpaceKeyIfNeeded(
                    mainView: MainLayoutBinding,
                    candidates: List<Candidate>,
                    insertString: String,
                ) {}
                override fun notifyAsyncZenzIfNeeded(
                    insertString: String,
                    result: ImeCandidateSuggestResult,
                ) {}
                override fun maybeLaunchZenzRerank(
                    requestToken: Long,
                    insertString: String,
                    baseCandidates: List<Candidate>,
                    plan: ImeCandidateZenzRerankPlan,
                    mainView: MainLayoutBinding,
                ) {}
            },
        )
        val short = Candidate(string = "a", length = 2u, type = 0, score = 0)
        val long = Candidate(string = "b", length = 4u, type = 0, score = 0)
        val filtered = coordinator.filterCandidatesForTail(listOf(short, long), "ab")
        assertEquals(2, filtered.size)
        tailLen = 0
        assertEquals(2, coordinator.filterCandidatesForTail(listOf(short, long), "ab").size)
    }

    @Test
    fun mergeBunsetsuAfterCandidateRequestAppliesComputedState() = runBlocking {
        var applied: ImeCandidatePresentationCoordinator.BunsetsuUiState? = null
        val coordinator = ImeCandidatePresentationCoordinator(
            object : ImeCandidatePresentationCoordinator.Host {
                override fun bunsetsuSeparationEnabled() = true
                override fun currentBunsetsuPositionList(): List<Int>? = listOf(1)
                override fun applyBunsetsuUiState(state: ImeCandidatePresentationCoordinator.BunsetsuUiState) {
                    applied = state
                }
                override fun stringInTailLength() = 0
                override fun shouldApplyCandidateResult(insertString: String, requestToken: Long) = true
                override suspend fun updateDisplayedCandidates(
                    insertString: String,
                    candidates: List<Candidate>,
                ) {}
                override suspend fun applyLiveConversion(
                    insertString: String,
                    candidates: List<Candidate>,
                    firstClauseResults: List<Candidate>,
                ) {}
                override fun updateBunsetsuSpaceKeyIfNeeded(
                    mainView: MainLayoutBinding,
                    candidates: List<Candidate>,
                    insertString: String,
                ) {}
                override fun notifyAsyncZenzIfNeeded(
                    insertString: String,
                    result: ImeCandidateSuggestResult,
                ) {}
                override fun maybeLaunchZenzRerank(
                    requestToken: Long,
                    insertString: String,
                    baseCandidates: List<Candidate>,
                    plan: ImeCandidateZenzRerankPlan,
                    mainView: MainLayoutBinding,
                ) {}
            },
        )
        val first = Candidate(string = "漢", length = 2u, type = 0, score = 0)
        val engine = BunsetsuCandidateResult(
            candidates = listOf(first),
            splitPatterns = listOf(listOf(2), listOf(1)),
            splitPatternByCandidateString = mapOf("漢" to listOf(2)),
        )
        coordinator.mergeBunsetsuAfterCandidateRequest("かんじ", listOf(first), engine)
        assertFalse(applied!!.splitPatterns.isEmpty())
        assertEquals(listOf(2), applied!!.positionList)
    }

    @Test
    fun applySuggestionResultToViewOrdersZenzBeforeDisplayAndSkipsStaleInput() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = ImeCandidatePresentationCoordinator(
            object : ImeCandidatePresentationCoordinator.Host {
                override fun bunsetsuSeparationEnabled() = false
                override fun currentBunsetsuPositionList(): List<Int>? = null
                override fun applyBunsetsuUiState(
                    state: ImeCandidatePresentationCoordinator.BunsetsuUiState,
                ) {}
                override fun stringInTailLength() = 0
                override fun shouldApplyCandidateResult(insertString: String, requestToken: Long) =
                    insertString == "live"
                override suspend fun updateDisplayedCandidates(
                    insertString: String,
                    candidates: List<Candidate>,
                ) {
                    events += "display"
                }
                override suspend fun applyLiveConversion(
                    insertString: String,
                    candidates: List<Candidate>,
                    firstClauseResults: List<Candidate>,
                ) {
                    events += "live"
                }
                override fun updateBunsetsuSpaceKeyIfNeeded(
                    mainView: MainLayoutBinding,
                    candidates: List<Candidate>,
                    insertString: String,
                ) {
                    events += "bunsetsu"
                }
                override fun notifyAsyncZenzIfNeeded(
                    insertString: String,
                    result: ImeCandidateSuggestResult,
                ) {
                    events += "zenz"
                }
                override fun maybeLaunchZenzRerank(
                    requestToken: Long,
                    insertString: String,
                    baseCandidates: List<Candidate>,
                    plan: ImeCandidateZenzRerankPlan,
                    mainView: MainLayoutBinding,
                ) {
                    events += "rerank"
                }
            },
        )
        val candidate = Candidate(string = "漢", length = 2u, type = 0, score = 0)
        val result = ImeCandidateSuggestResult(
            candidates = listOf(candidate),
            bunsetsuResult = null,
            emitAsyncZenzGeneration = true,
        )
        coordinator.applySuggestionResultToView(
            insertString = "stale",
            mainView = mock(),
            result = result,
            requestToken = 1L,
        )
        assertEquals(listOf("zenz"), events)

        events.clear()
        coordinator.applySuggestionResultToView(
            insertString = "live",
            mainView = mock(),
            result = result,
            requestToken = 2L,
        )
        assertEquals(listOf("zenz", "display", "live", "bunsetsu"), events)
    }
}
