package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntryMapper
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AzooKeyLatticeDecoderMorphologicalTest {
    @Test
    fun morphologicalCostChangesRanking() {
        val nodes = listOf(
            latticeNode(surface = "あ", reading = "a", start = 0, end = 1, value = -1f, mid = 100),
            latticeNode(surface = "い", reading = "i", start = 1, end = 2, value = -1f, mid = 200),
            latticeNode(surface = "愛", reading = "ai", start = 0, end = 2, value = -4f, mid = 200),
        )
        val withoutMm = AzooKeyLatticeDecoder(
            connectionCost = { _, _ -> 0f },
            morphologicalCost = { _, _ -> 0f },
        ).decode(inputLength = 2, nodes = nodes, nBest = 2)
        val withMm = AzooKeyLatticeDecoder(
            connectionCost = { _, _ -> 0f },
            morphologicalCost = { former, latter ->
                if (former == 100 && latter == 200) -50f else 0f
            },
        ).decode(inputLength = 2, nodes = nodes, nBest = 2)
        assertEquals("あい", withoutMm.first().string)
        assertEquals("愛", withMm.first().string)
        assertNotEquals(withoutMm.first().string, withMm.first().string)
    }

    private fun latticeNode(
        surface: String,
        reading: String,
        start: Int,
        end: Int,
        value: Float,
        mid: Int,
    ): AzooKeyLatticeNode {
        return AzooKeyLatticeNode(
            entry = AzooKeyDictionaryEntryMapper.memory(
                surface = surface,
                reading = reading,
                leftId = 1285,
                rightId = 1285,
                legacyScore = value.toInt(),
                readingLength = reading.length,
            ).copy(value = value, mid = mid),
            startIndex = start,
            endIndex = end,
        )
    }
}