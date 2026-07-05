package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ZenzaiCandidateEvaluationResultTest {
    @Test
    fun parsePassWithRichAlternatives() {
        val parsed = ZenzaiCandidateEvaluationResult.parse("PASS:-1.5|ALT:0.8:東京都|ALT:0.4:東京")
        require(parsed is ZenzaiCandidateEvaluationResult.Pass)
        assertEquals(-1.5f, parsed.score, 0.001f)
        assertEquals(2, parsed.alternativeConstraints.size)
        assertEquals(0.8f, parsed.alternativeConstraints[0].probabilityRatio, 0.001f)
        assertEquals("東京都", parsed.alternativeConstraints[0].prefix)
    }
}
