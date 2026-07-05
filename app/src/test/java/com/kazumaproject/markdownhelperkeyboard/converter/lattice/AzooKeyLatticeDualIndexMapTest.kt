package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputElement
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputPiece
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyLatticeDualIndexMapTest {
    @Test
    fun directInputProducesBothIndices() {
        val text = ComposingText.fromConvertTarget("しか")
        val map = AzooKeyLatticeDualIndexMap(text)
        val indices = map.indices(
            inputCount = text.input.size,
            surfaceCount = text.convertTarget.length,
        )
        assertEquals(2, indices.size)
        assertEquals(
            AzooKeyLatticeDualIndexMap.DualIndex.BothIndex(inputIdx = 0, surfaceIdx = 0),
            indices[0],
        )
        assertEquals(
            AzooKeyLatticeDualIndexMap.DualIndex.BothIndex(inputIdx = 1, surfaceIdx = 1),
            indices[1],
        )
    }

    @Test
    fun compositionSeparatorExposesSurfaceOnlyGap() {
        val text = ComposingText.fromInputElements(
            listOf(
                InputElement(InputPiece.Character('か'), InputStyle.Direct),
                InputElement(InputPiece.CompositionSeparator, InputStyle.Frozen),
                InputElement(InputPiece.Character('い'), InputStyle.Direct),
            ),
        )
        val map = AzooKeyLatticeDualIndexMap(text)
        val indices = map.indices(text.input.size, text.convertTarget.length)
        assertEquals(
            AzooKeyLatticeDualIndexMap.DualIndex.BothIndex(inputIdx = 0, surfaceIdx = 0),
            indices[0],
        )
        assertEquals(
            AzooKeyLatticeDualIndexMap.DualIndex.BothIndex(inputIdx = 1, surfaceIdx = 1),
            indices[1],
        )
        assertEquals(AzooKeyLatticeDualIndexMap.DualIndex.InputIndex(2), indices[2])
    }
}