package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateLanePresentationTest {
    @Test
    fun supplementaryEmojiExcludedFromLiveConversion() {
        val emoji = Candidate(
            string = "🙇",
            type = CandidateType.EMOJI_SPECIAL,
            length = 1u,
            score = 100,
            value = 100f,
        )
        val nbest = Candidate(
            string = "ごめんなさい",
            type = CandidateType.NBEST,
            length = 6u,
            score = 50,
            value = 50f,
        )
        val (main, supplementary) = CandidateLanePresentation.splitMainAndSupplementary(
            listOf(nbest, emoji),
        )
        assertEquals(listOf("ごめんなさい"), main.map { it.string })
        assertEquals(listOf("🙇"), supplementary.map { it.string })
        assertFalse(CandidateLanePresentation.forLiveConversion(main).any { it.string == "🙇" })
    }

    @Test
    fun mergeForDisplayAppendsSupplementaryWithoutDuplicates() {
        val main = listOf(
            Candidate(string = "日本", type = CandidateType.NBEST, length = 2u, score = 1, value = 1f),
        )
        val supplementary = listOf(
            Candidate(string = "🙇", type = CandidateType.EMOJI_SPECIAL, length = 1u, score = 1, value = 1f),
            Candidate(string = "日本", type = CandidateType.EMOJI_SPECIAL, length = 2u, score = 1, value = 1f),
        )
        val merged = CandidateLanePresentation.mergeForDisplay(main, supplementary)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.string == "🙇" })
    }
}
