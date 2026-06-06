package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidatePostProcessorTest {
    @Test
    fun processFiltersNgWordsAndRemovesDuplicates() = runTest {
        val processor = CandidatePostProcessor(
            isNgWordEnabled = true,
            ngWordPattern = Regex("NG"),
            isOrderOverrideEnabled = false,
            applyOrderOverride = { _, candidates -> candidates.reversed() },
        )

        val result = processor.process(
            input = "あず",
            candidates = listOf(
                candidate("候補"),
                candidate("NG候補"),
                candidate("候補"),
                candidate("別候補"),
            )
        )

        assertEquals(listOf("候補", "別候補"), result.map { it.string })
    }

    @Test
    fun processAppliesOrderOverrideWhenEnabled() = runTest {
        val processor = CandidatePostProcessor(
            isNgWordEnabled = false,
            ngWordPattern = Regex("NG"),
            isOrderOverrideEnabled = true,
            applyOrderOverride = { input, candidates ->
                assertEquals("あず", input)
                candidates.sortedByDescending { it.string }
            },
        )

        val result = processor.process(
            input = "あず",
            candidates = listOf(candidate("A"), candidate("C"), candidate("B"))
        )

        assertEquals(listOf("C", "B", "A"), result.map { it.string })
    }

    @Test
    fun processKeepsCandidatesWhenNgFilteringDisabledEvenWithEmptyPattern() = runTest {
        val processor = CandidatePostProcessor(
            isNgWordEnabled = false,
            ngWordPattern = Regex(""),
            isOrderOverrideEnabled = false,
            applyOrderOverride = { _, candidates -> candidates.reversed() },
        )

        val result = processor.process(
            input = "あず",
            candidates = listOf(candidate("A"), candidate("B"))
        )

        assertEquals(listOf("A", "B"), result.map { it.string })
    }

    @Test
    fun processSkipsOrderOverrideWhenDisabled() = runTest {
        var orderCallCount = 0
        val processor = CandidatePostProcessor(
            isNgWordEnabled = false,
            ngWordPattern = Regex("NG"),
            isOrderOverrideEnabled = false,
            applyOrderOverride = { _, candidates ->
                orderCallCount += 1
                candidates.reversed()
            },
        )

        val result = processor.process(
            input = "あず",
            candidates = listOf(candidate("A"), candidate("B"))
        )

        assertEquals(0, orderCallCount)
        assertEquals(listOf("A", "B"), result.map { it.string })
    }

    private fun candidate(string: String): Candidate {
        return Candidate(
            string = string,
            type = CandidateType.NBEST,
            length = string.length.toUByte(),
            score = 0,
        )
    }
}
