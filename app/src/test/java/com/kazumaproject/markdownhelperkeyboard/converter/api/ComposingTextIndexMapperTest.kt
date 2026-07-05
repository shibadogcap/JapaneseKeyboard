package com.kazumaproject.markdownhelperkeyboard.converter.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposingTextIndexMapperTest {
    @Test
    fun directInputMapsOneToOne() {
        val text = ComposingText.fromConvertTarget("しかい")
        val map = text.inputIndexToSurfaceIndexMap()
        assertEquals(0, map[0])
        assertEquals(1, map[1])
        assertEquals(2, map[2])
        assertEquals(3, map[3])
    }

    @Test
    fun compositionSeparatorSplitsSegments() {
        val elements = listOf(
            InputElement(InputPiece.Character('か'), InputStyle.Direct),
            InputElement(InputPiece.Character('ん'), InputStyle.Direct),
            InputElement(InputPiece.CompositionSeparator, InputStyle.Frozen),
            InputElement(InputPiece.Character('し'), InputStyle.Direct),
        )
        val text = ComposingText.fromInputElements(elements)
        assertEquals("かんし", text.convertTarget)
        val map = text.inputIndexToSurfaceIndexMap()
        assertEquals(0, map[0])
        assertEquals(1, map[1])
        assertEquals(2, map[2])
        assertEquals(2, map[3])
    }
}