package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateTypeTest {
    @Test
    fun testLegacyTypesMappedToSpecialLane() {
        val emojiCandidate = Candidate(string = "🙏", type = CandidateType.EMOJI_LEGACY, length = 1u, score = 0)
        val emoticonCandidate = Candidate(string = "m(_ _)m", type = CandidateType.EMOTICON_LEGACY, length = 7u, score = 0)
        val symbolCandidate = Candidate(string = "☆", type = CandidateType.SYMBOL_LEGACY, length = 1u, score = 0)

        assertEquals(CandidateLane.Special, CandidateType.laneOf(emojiCandidate))
        assertEquals(CandidateLane.Special, CandidateType.laneOf(emoticonCandidate))
        assertEquals(CandidateLane.Special, CandidateType.laneOf(symbolCandidate))
    }

    @Test
    fun testAdjustCandidateWithEmptyDataReturnsUnmodified() {
        val candidate = Candidate(string = "あ", type = (3).toByte(), length = 1u, score = 0, data = emptyList())
        assertEquals(candidate, candidate.adjustCandidate())
    }

    @Test
    fun testAdjustCandidateWithLongLastReadingReturnsUnmodified() {
        val entry = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "今日",
            reading = "きょう",
            wordCost = 0,
            leftId = 0,
            rightId = 0,
            mid = 0,
        )
        val candidate = Candidate(string = "今日", type = (1).toByte(), length = 2u, score = 0, data = listOf(entry))
        assertEquals(candidate, candidate.adjustCandidate())
    }

    @Test
    fun testAdjustCandidateWithShortLastReadingAdjustsSurface() {
        val entry1 = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "変換",
            reading = "へんかん",
            wordCost = 0,
            leftId = 0,
            rightId = 0,
            mid = 0,
        )
        val entry2 = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "固",
            reading = "こ",
            wordCost = 0,
            leftId = 0,
            rightId = 0,
            mid = 0,
        )
        val candidate = Candidate(
            string = "変換固",
            type = (1).toByte(),
            length = 3u,
            score = 0,
            data = listOf(entry1, entry2)
        )
        val adjusted = candidate.adjustCandidate()
        assertEquals("変換こ", adjusted.string)
        assertEquals("へんかんこ", adjusted.yomi)
        assertEquals(2, adjusted.data.size)
        assertEquals("こ", adjusted.data[1].surface)
    }
}

