package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzModelOutputPolicyTest {
    @Test
    fun acceptsJapaneseConstraintText() {
        assertTrue(ZenzModelOutputPolicy.isValidConstraintText("東京"))
        assertTrue(ZenzModelOutputPolicy.isValidConstraintText("→"))
    }

    @Test
    fun rejectsHangulConstraintText() {
        assertFalse(ZenzModelOutputPolicy.isValidConstraintText("한국"))
        assertFalse(ZenzModelOutputPolicy.isValidConstraintText("東京한국"))
    }

    @Test
    fun sanitizeGeneratedReadingRejectsHangul() {
        assertNull(ZenzModelOutputPolicy.sanitizeGeneratedReading("  한글  "))
        assertEquals("てすと", ZenzModelOutputPolicy.sanitizeGeneratedReading(" てすと "))
    }
}

class ZenzDictionaryCandidateResolverTest {
    private val dictionary = listOf(
        Candidate(string = "司会", type = CandidateType.NBEST, length = 3u, score = 0),
        Candidate(string = "視界", type = CandidateType.NBEST, length = 3u, score = 0),
    )

    @Test
    fun resolveSurfaceReturnsDictionaryMatch() {
        assertEquals(
            "視界",
            ZenzDictionaryCandidateResolver.resolveSurface(
                dictionaryCandidates = dictionary,
                constraint = "視界",
                fallback = "司会",
            ),
        )
    }

    @Test
    fun resolveSurfaceFallsBackWhenConstraintNotInDictionary() {
        assertEquals(
            "司会",
            ZenzDictionaryCandidateResolver.resolveSurface(
                dictionaryCandidates = dictionary,
                constraint = "世界",
                fallback = "司会",
            ),
        )
    }
}

class ZenzaiCandidateEvaluationResultHangulTest {
    @Test
    fun parseWholeRejectsHangul() {
        val parsed = ZenzaiCandidateEvaluationResult.parse("WHOLE:한국")
        assertEquals(ZenzaiCandidateEvaluationResult.Error, parsed)
    }

    @Test
    fun parseFixRejectsHangul() {
        val parsed = ZenzaiCandidateEvaluationResult.parse("FIX:한국")
        assertEquals(ZenzaiCandidateEvaluationResult.Error, parsed)
    }

    @Test
    fun parsePassSkipsHangulAlternatives() {
        val parsed = ZenzaiCandidateEvaluationResult.parse("PASS:-1.5|ALT:0.8:한국|ALT:0.4:東京")
        require(parsed is ZenzaiCandidateEvaluationResult.Pass)
        assertEquals(1, parsed.alternativeConstraints.size)
        assertEquals("東京", parsed.alternativeConstraints.first().prefix)
    }
}
