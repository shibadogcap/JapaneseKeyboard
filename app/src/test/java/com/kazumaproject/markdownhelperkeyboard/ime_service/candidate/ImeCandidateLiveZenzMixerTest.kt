package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyLiveZenzMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeCandidateLiveZenzMixerTest {
    @Test
    fun mergePromotesZenzCandidateAheadOfDictionary() {
        val dictionary = listOf(
            Candidate(string = "司会", type = 1, length = 3u, score = 3000),
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
        val merged = AzooKeyLiveZenzMerge.mergeIfApplicable(
            insertReading = "しかい",
            dictionaryCandidates = dictionary,
            zenzCandidates = zenz,
        )
        requireNotNull(merged)
        assertEquals("視界", merged.first().string)
    }

    @Test
    fun mergeReturnsNullWhenReadingMismatch() {
        val merged = AzooKeyLiveZenzMerge.mergeIfApplicable(
            insertReading = "しかい",
            dictionaryCandidates = listOf(
                Candidate(string = "司会", type = 1, length = 3u, score = 3000),
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

    @Test
    fun mergeCopiesDictionaryDataWhenSurfaceMatches() {
        val entry = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "司会",
            reading = "シカイ",
            wordCost = 0,
            leftId = 0,
            rightId = 0,
            mid = 501,
        )
        val dictionary = listOf(
            Candidate(
                string = "司会",
                type = 1,
                length = 3u,
                score = 3000,
                data = listOf(entry),
                yomi = "シカイ",
            ),
        )
        val zenz = listOf(
            ZenzCandidate(
                string = "司会",
                type = CandidateType.ZENZ,
                length = 3u,
                score = 2500,
                originalString = "しかい",
            ),
        )
        val merged = AzooKeyLiveZenzMerge.mergeIfApplicable(
            insertReading = "しかい",
            dictionaryCandidates = dictionary,
            zenzCandidates = zenz,
        )
        requireNotNull(merged)
        assertEquals(1, merged.first().data.size)
        assertEquals("シカイ", merged.first().data.first().reading)
    }

    @Test
    fun mergePopulatesYomiForZenzCandidates() {
        val dictionary = listOf(
            Candidate(string = "司会", type = 1, length = 3u, score = 3000),
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
        val merged = AzooKeyLiveZenzMerge.mergeIfApplicable(
            insertReading = "しかい",
            dictionaryCandidates = dictionary,
            zenzCandidates = zenz,
        )
        requireNotNull(merged)
        val zenzResult = merged.firstOrNull { it.string == "視界" }
        requireNotNull(zenzResult)
        assertEquals("しかい", zenzResult.yomi)
    }

    @Test
    fun mergePreservesHalfWidthKatakanaVariants() {
        val dictionary = listOf(
            Candidate(string = "東京", type = 1, length = 2u, score = 3000, value = 3000f),
            Candidate(string = "トウキョウ", type = CandidateType.KATAKANA, length = 5u, score = -14, value = -14f),
            Candidate(string = "ﾄｳｷｮｳ", type = CandidateType.NBEST, length = 4u, score = -15, value = -15f),
        )
        val zenz = listOf(
            ZenzCandidate(
                string = "東京",
                type = CandidateType.ZENZ,
                length = 2u,
                score = 3500,
                originalString = "とうきょう",
            ),
        )
        val merged = AzooKeyLiveZenzMerge.mergeIfApplicable(
            insertReading = "とうきょう",
            dictionaryCandidates = dictionary,
            zenzCandidates = zenz,
        )
        requireNotNull(merged)
        assertTrue(merged.any { it.string == "ﾄｳｷｮｳ" })
    }
}
