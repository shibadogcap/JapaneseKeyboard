package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntryMapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyLatticeDecoderTest {
    @Test
    fun decodePrefersSingleSpanOverSegmentedPath() {
        val nodes = listOf(
            latticeNode(surface = "司会", reading = "しかい", start = 0, end = 3, value = -5f),
            latticeNode(surface = "し", reading = "し", start = 0, end = 1, value = -8f),
            latticeNode(surface = "かい", reading = "かい", start = 1, end = 3, value = -7f),
        )
        val candidates = AzooKeyLatticeDecoder().decode(
            inputLength = 3,
            nodes = nodes,
            nBest = 3,
        )
        assertEquals("司会", candidates.first().string)
        assertTrue(candidates.map { it.string }.contains("しかい"))
    }

    private fun latticeNode(
        surface: String,
        reading: String,
        start: Int,
        end: Int,
        value: Float,
    ): AzooKeyLatticeNode {
        return AzooKeyLatticeNode(
            entry = AzooKeyDictionaryEntryMapper.memory(
                surface = surface,
                reading = reading,
                leftId = 1285,
                rightId = 1285,
                legacyScore = value.toInt(),
                readingLength = reading.length,
            ).copy(value = value),
            startIndex = start,
            endIndex = end,
        )
    }
}