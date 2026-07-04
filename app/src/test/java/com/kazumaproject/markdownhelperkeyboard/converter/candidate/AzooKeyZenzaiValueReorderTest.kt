package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyZenzaiValueReorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AzooKeyZenzaiValueReorderTest {
    @Test
    fun rerankByZenzScoresPromotesHighZenzCandidate() {
        val result = AzooKeyZenzaiValueReorder.rerankByZenzScores(
            candidates = listOf(
                candidate("低い辞書候補", score = 9000, value = -9f),
                candidate("文脈に合う候補", score = 1000, value = -5f),
                candidate("中間候補", score = 5000, value = -7f),
            ),
            rawZenzScores = listOf(0.1f, 10.0f, 0.2f),
        )

        assertEquals(listOf("文脈に合う候補", "中間候補", "低い辞書候補"), result?.map { it.string })
    }

    @Test
    fun rerankReturnsNullWhenScoreSizeDoesNotMatch() {
        val result = AzooKeyZenzaiValueReorder.rerankByZenzScores(
            candidates = listOf(candidate("候補", score = 100)),
            rawZenzScores = emptyList(),
        )

        assertNull(result)
    }

    @Test
    fun rerankReturnsNullWhenAllZenzScoresAreNotFinite() {
        val result = AzooKeyZenzaiValueReorder.rerankByZenzScores(
            candidates = listOf(candidate("候補", score = 100)),
            rawZenzScores = listOf(Float.NaN),
        )

        assertNull(result)
    }

    @Test
    fun rerankKeepsOriginalOrderWhenZenzScoresTie() {
        val result = AzooKeyZenzaiValueReorder.rerankByZenzScores(
            candidates = listOf(
                candidate("先", score = 100),
                candidate("後", score = 100),
            ),
            rawZenzScores = listOf(1.0f, 1.0f),
        )

        assertEquals(listOf("先", "後"), result?.map { it.string })
    }

    @Test
    fun reorderTopValuesSortsDescendingValues() {
        val reordered = AzooKeyZenzaiValueReorder.reorderTopValues(
            listOf(
                candidate("a", score = 1, value = 1f),
                candidate("b", score = 2, value = 5f),
                candidate("c", score = 3, value = 3f),
            ),
        )
        assertEquals(listOf(5f, 3f, 1f), reordered.map { it.value })
    }

    private fun candidate(
        string: String,
        score: Int,
        value: AzooKeyPValue = score.toFloat(),
    ): Candidate {
        return Candidate(
            string = string,
            type = CandidateType.NBEST,
            length = string.length.toUByte(),
            score = score,
            value = value,
        )
    }
}
