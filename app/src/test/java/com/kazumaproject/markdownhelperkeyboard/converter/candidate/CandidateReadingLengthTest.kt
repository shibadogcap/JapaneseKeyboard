package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.prefixComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateReadingLengthTest {
    @Test
    fun effectiveRubyCountPrefersDataReadingSum() {
        val candidate = Candidate(
            string = "東京",
            type = CandidateType.NBEST,
            length = 2u,
            score = 0,
            rubyCount = 5,
            data = listOf(
                AzooKeyDictionaryEntry(
                    surface = "東京",
                    reading = "トウキョウ",
                    leftId = 2,
                    rightId = 2,
                    mid = 501,
                    wordCost = 0,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                ),
            ),
        )
        assertEquals(5, candidate.effectiveRubyCount)
        assertTrue(candidate.effectiveRubyCount != candidate.length.toInt())
    }

    @Test
    fun makePrefixClauseCandidateSetsSurfaceComposingCount() {
        val candidate = makePrefixClauseCandidate(
            listOf(
                AzooKeyDictionaryEntry(
                    surface = "東京",
                    reading = "トウキョウ",
                    leftId = 2,
                    rightId = 2,
                    mid = 501,
                    wordCost = 0,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                ),
            ),
        )
        assertEquals(ComposingCount.SurfaceCount(5), candidate.composingCount)
    }

    @Test
    fun prefixCompleteUsesReadingLengthNotSurfaceLength() {
        val composing = ComposingText.fromConvertTarget("とうきょうは")
        val trimmed = composing.prefixComplete(ComposingCount.SurfaceCount(5))
        assertEquals("は", trimmed.convertTarget)
    }
}
