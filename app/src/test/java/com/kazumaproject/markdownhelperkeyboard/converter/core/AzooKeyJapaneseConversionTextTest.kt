package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyJapaneseConversionTextTest {
    @Test
    fun acceptsJapanesePrefixes() {
        assertTrue(AzooKeyJapaneseConversionText.isValidPrefix("東京"))
        assertTrue(AzooKeyJapaneseConversionText.isValidPrefix("とう"))
        assertTrue(AzooKeyJapaneseConversionText.isValidPrefix("トウ"))
    }

    @Test
    fun rejectsHangulPrefixes() {
        assertFalse(AzooKeyJapaneseConversionText.isValidPrefix("한국"))
        assertFalse(AzooKeyJapaneseConversionText.isValidPrefix("서울"))
    }

    @Test
    fun parsePassFiltersHangulAlternatives() {
        val parsed = ZenzaiCandidateEvaluationResult.parse(
            "PASS:-1.0|ALT:0.8:東京|ALT:0.7:한국",
        )
        require(parsed is ZenzaiCandidateEvaluationResult.Pass)
        assertEquals(1, parsed.alternativeConstraints.size)
        assertEquals("東京", parsed.alternativeConstraints.first().prefix)
    }
}
