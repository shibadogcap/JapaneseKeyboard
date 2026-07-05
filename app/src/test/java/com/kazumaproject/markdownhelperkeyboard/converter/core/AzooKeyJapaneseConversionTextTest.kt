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
    fun acceptsPureHalfWidthKatakanaForDisplay() {
        assertFalse(AzooKeyJapaneseConversionText.shouldRejectDisplayedCandidate("ﾄｳｷｮｳ"))
        assertFalse(AzooKeyJapaneseConversionText.shouldRejectDisplayedCandidate("ｱｲｳ"))
    }

    @Test
    fun acceptsHalfWidthKanaSurfacesForEngineValidation() {
        assertTrue(AzooKeyJapaneseConversionText.isValidCandidateSurface("ﾄｳｷｮｳ"))
        assertTrue(AzooKeyJapaneseConversionText.isValidCandidateSurface("ｱｲｳ"))
    }

    @Test
    fun rejectsMixedHangulSurfaces() {
        assertFalse(AzooKeyJapaneseConversionText.isValidCandidateSurface("東京한국"))
        assertTrue(AzooKeyJapaneseConversionText.shouldRejectDisplayedCandidate("ﾄｳ한국"))
    }

    @Test
    fun acceptsParenthesisAndEmojiSurfaces() {
        assertTrue(AzooKeyJapaneseConversionText.isValidCandidateSurface("（）"))
        assertTrue(AzooKeyJapaneseConversionText.isValidCandidateSurface("「」"))
        assertTrue(AzooKeyJapaneseConversionText.isValidCandidateSurface("🙇"))
    }

    @Test
    fun acceptsDecoratedAlphanumericSurfaces() {
        assertTrue(AzooKeyJapaneseConversionText.isValidCandidateSurface("abc"))
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

    @Test
    fun acceptsCompoundEmojiWithZeroWidthJoiner() {
        assertTrue(AzooKeyJapaneseConversionText.isValidCandidateSurface("👨‍👩‍👧"))
    }

    @Test
    fun filterDisplayedCandidatesRemovesHangul() {
        val candidates = listOf(
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
                string = "東京",
                type = 3,
                length = 2u,
                score = 0,
            ),
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
                string = "한국",
                type = 3,
                length = 2u,
                score = 0,
            ),
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
                string = "ﾄｳｷｮｳ",
                type = 3,
                length = 4u,
                score = -15,
            ),
        )
        val filtered = AzooKeyJapaneseConversionText.filterDisplayedCandidates(candidates)
        assertEquals(2, filtered.size)
        assertEquals("東京", filtered[0].string)
        assertEquals("ﾄｳｷｮｳ", filtered[1].string)
    }
}
