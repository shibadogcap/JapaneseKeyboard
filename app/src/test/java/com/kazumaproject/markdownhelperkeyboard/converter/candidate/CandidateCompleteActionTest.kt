package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateCompleteActionTest {
    @Test
    fun appropriateActionsMoveCursorInsideParentheses() {
        val candidate = Candidate(
            string = "()",
            type = CandidateType.SYMBOL_SPECIAL,
            length = 1u,
            score = 0,
        ).applyAppropriateActions()

        assertEquals(1, candidate.actions.size)
        val action = candidate.actions.first() as CandidateCompleteAction.MoveCursor
        assertEquals(-1, action.offset)
    }

    @Test
    fun appropriateActionsCoverFullWidthParentheses() {
        val candidate = Candidate(
            string = "（）",
            type = CandidateType.SYMBOL_SPECIAL,
            length = 1u,
            score = 0,
        ).applyAppropriateActions()

        assertTrue(candidate.actions.any { it is CandidateCompleteAction.MoveCursor })
    }
}
