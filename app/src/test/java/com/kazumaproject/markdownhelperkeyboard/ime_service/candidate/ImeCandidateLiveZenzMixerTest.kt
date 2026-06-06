package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImeCandidateLiveZenzMixerTest {
    @Test
    fun mergePromotesZenzCandidateAheadOfDictionary() {
        val dictionary = listOf(
            Candidate(string = "司会", type = 1, length = 3u, score = 100),
        )
        val zenz = listOf(
            ZenzCandidate(
                string = "視界",
                type = CandidateType.ZENZ,
                length = 3u,
                score = 2000,
                originalString = "しかい",
            ),
        )
        val merged = ImeCandidateLiveZenzMixer.mergeIfApplicable(
            insertReading = "しかい",
            dictionaryCandidates = dictionary,
            zenzCandidates = zenz,
        )
        requireNotNull(merged)
        assertEquals("視界", merged.first().string)
    }

    @Test
    fun mergeReturnsNullWhenReadingMismatch() {
        val merged = ImeCandidateLiveZenzMixer.mergeIfApplicable(
            insertReading = "しかい",
            dictionaryCandidates = listOf(
                Candidate(string = "司会", type = 1, length = 3u, score = 100),
            ),
            zenzCandidates = listOf(
                ZenzCandidate(
                    string = "視界",
                    type = CandidateType.ZENZ,
                    length = 3u,
                    score = 2000,
                    originalString = "ちがう",
                ),
            ),
        )
        assertNull(merged)
    }
}