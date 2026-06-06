package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ZenzaiAlternativeConstraintPolicyTest {
    @Test
    fun picksDictionaryCandidateMatchingConstraintPrefix() = runTest {
        val request = ZenzPredictiveRequest(
            insertReading = "とうきょう",
            leftContext = "",
            dictionaryCandidates = listOf("東京駅", "東京都庁", "京都"),
            nBest = 4,
            config = ZenzConversionConfig(profile = "test"),
        )
        val result = ZenzaiAlternativeConstraintPolicy.resolveBestCandidate(
            request = request,
            constraints = listOf(
                ZenzaiCandidateEvaluationResult.AlternativeConstraint(
                    probabilityRatio = 1f,
                    prefix = "東京都",
                ),
            ),
            generateWithPrefixContext = { "generated" },
            toZenzCandidate = { surface, type ->
                ZenzCandidate(
                    string = surface,
                    type = type,
                    length = request.insertReading.length.toUByte(),
                    score = 2000,
                    originalString = request.insertReading,
                )
            },
        )

        assertEquals("東京都庁", result?.string)
        assertEquals(CandidateType.ZENZ_CONTEXTUAL, result?.type)
    }
}