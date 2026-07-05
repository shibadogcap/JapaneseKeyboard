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
            dictionaryCandidates = listOf(
                com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
                    string = "東京駅",
                    type = CandidateType.NBEST,
                    length = 3.toUByte(),
                    score = 0,
                    yomi = "とうきょうえき"
                ),
                com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
                    string = "東京都庁",
                    type = CandidateType.NBEST,
                    length = 4.toUByte(),
                    score = 0,
                    yomi = "とうきょうとちょう"
                ),
                com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
                    string = "京都",
                    type = CandidateType.NBEST,
                    length = 2.toUByte(),
                    score = 0,
                    yomi = "きょうと"
                )
            ),
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

    @Test
    fun preservesSpecialCandidateTypeInConstraintPrefix() = runTest {
        val request = ZenzPredictiveRequest(
            insertReading = "a",
            leftContext = "",
            dictionaryCandidates = listOf(
                com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
                    string = "Ⓐ",
                    type = CandidateType.TYPOGRAPHY_SPECIAL,
                    length = 1.toUByte(),
                    score = 0,
                    yomi = "a"
                )
            ),
            nBest = 4,
            config = ZenzConversionConfig(profile = "test"),
        )
        val result = ZenzaiAlternativeConstraintPolicy.resolveBestCandidate(
            request = request,
            constraints = listOf(
                ZenzaiCandidateEvaluationResult.AlternativeConstraint(
                    probabilityRatio = 1f,
                    prefix = "Ⓐ",
                ),
            ),
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

        assertEquals("Ⓐ", result?.string)
        assertEquals(CandidateType.TYPOGRAPHY_SPECIAL, result?.type)
    }
}