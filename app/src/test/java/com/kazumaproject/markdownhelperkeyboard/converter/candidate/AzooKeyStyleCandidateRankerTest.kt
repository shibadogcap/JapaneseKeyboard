package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyStyleCandidateRankerTest {
    @Test
    fun rankDedupesBySurfaceKeepingHigherValue() {
        val ranked = AzooKeyStyleCandidateRanker.rank(
            listOf(
                Candidate(string = "司会", type = 1, length = 2u, score = 10, value = 10f),
                Candidate(string = "司会", type = 1, length = 2u, score = 99, value = 99f),
                Candidate(string = "視界", type = 1, length = 2u, score = 50, value = 50f),
            )
        )
        assertEquals(listOf("司会", "視界"), ranked.map { it.string })
        assertEquals(99f, ranked.first().value)
    }
}