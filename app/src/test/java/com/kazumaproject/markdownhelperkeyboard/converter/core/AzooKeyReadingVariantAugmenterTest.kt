package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyReadingVariantAugmenterTest {
    @Test
    fun augmentAppendsHalfWidthKatakanaWhenMissing() {
        val candidates = listOf(
            Candidate(string = "東京", type = 1, length = 2u, score = 3000, value = 3000f),
        )
        val augmented = AzooKeyReadingVariantAugmenter.augment(
            candidates = candidates,
            reading = "とうきょう",
            includeHalfWidthKana = true,
        )
        assertEquals(4, augmented.size)
        assertTrue(augmented.any { it.string == "ﾄｳｷｮｳ" })
    }

    @Test
    fun augmentSkipsHalfWidthWhenDisabled() {
        val candidates = listOf(
            Candidate(string = "東京", type = 1, length = 2u, score = 3000, value = 3000f),
        )
        val augmented = AzooKeyReadingVariantAugmenter.augment(
            candidates = candidates,
            reading = "とうきょう",
            includeHalfWidthKana = false,
        )
        assertTrue(augmented.none { it.string == "ﾄｳｷｮｳ" })
    }
}
