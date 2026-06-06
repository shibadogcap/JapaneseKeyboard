package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzaiCandidateEvaluationResultTest {
    @Test
    fun parsePassResult() {
        val result = ZenzaiCandidateEvaluationResult.parse("PASS:-12.5")

        assertEquals(ZenzaiCandidateEvaluationResult.Pass(score = -12.5f), result)
    }

    @Test
    fun parsePassWithAlternativeConstraints() {
        val result = ZenzaiCandidateEvaluationResult.parse(
            "PASS:-12.5|ALT:0.9:東京都|ALT:0.4:東京",
        )

        assertEquals(
            ZenzaiCandidateEvaluationResult.Pass(
                score = -12.5f,
                alternativeConstraints = listOf(
                    ZenzaiCandidateEvaluationResult.AlternativeConstraint(
                        probabilityRatio = 0.9f,
                        prefix = "東京都",
                    ),
                    ZenzaiCandidateEvaluationResult.AlternativeConstraint(
                        probabilityRatio = 0.4f,
                        prefix = "東京",
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun parseFixRequiredResult() {
        val result = ZenzaiCandidateEvaluationResult.parse("FIX:東京都")

        assertEquals(ZenzaiCandidateEvaluationResult.FixRequired(prefix = "東京都"), result)
    }

    @Test
    fun parseWholeResult() {
        val result = ZenzaiCandidateEvaluationResult.parse("WHOLE:今日は晴れ")

        assertEquals(ZenzaiCandidateEvaluationResult.WholeResult(result = "今日は晴れ"), result)
    }

    @Test
    fun parseUnknownAsError() {
        assertTrue(ZenzaiCandidateEvaluationResult.parse("UNKNOWN") is ZenzaiCandidateEvaluationResult.Error)
        assertTrue(ZenzaiCandidateEvaluationResult.parse(null) is ZenzaiCandidateEvaluationResult.Error)
    }
}