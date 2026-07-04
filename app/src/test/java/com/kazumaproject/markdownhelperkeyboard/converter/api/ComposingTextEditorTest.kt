package com.kazumaproject.markdownhelperkeyboard.converter.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposingTextEditorTest {
    @Test
    fun insertAndDeleteAtCursor() {
        var text = ComposingText.fromConvertTarget("しか")
        text = text.insertDirectAtCursor("い")
        assertEquals("しかい", text.convertTarget)
        assertEquals(3, text.convertTargetCursorPosition)

        text = text.deleteBackwardFromCursor(1)
        assertEquals("しか", text.convertTarget)
        assertEquals(2, text.convertTargetCursorPosition)
    }

    @Test
    fun deleteBackwardFromRoman2KanaRun() {
        val transducer = AzooKeyRoman2KanaTransducer.fromMap(
            mapOf("kai" to ("かい" to 3)),
        )
        var text = ComposingText.fromConvertTarget("か")
        text = text.appendRoman2KanaCharAtEnd('k', transducer)
        text = text.appendRoman2KanaCharAtEnd('a', transducer)
        text = text.appendRoman2KanaCharAtEnd('i', transducer)
        assertEquals("かかい", text.convertTarget)
        text = text.deleteBackwardFromCursor(1, transducer)
        assertEquals("かk", text.convertTarget)
        text = text.deleteBackwardFromCursor(1, transducer)
        assertEquals("か", text.convertTarget)
    }

    @Test
    fun appendRoman2KanaCharAtEndMergesRun() {
        val transducer = AzooKeyRoman2KanaTransducer.fromMap(
            mapOf(
                "ka" to ("か" to 2),
                "kai" to ("かい" to 3),
            ),
        )
        var text = ComposingText.fromConvertTarget("か")
        text = text.appendRoman2KanaCharAtEnd('k', transducer)
        text = text.appendRoman2KanaCharAtEnd('a', transducer)
        text = text.appendRoman2KanaCharAtEnd('i', transducer)
        assertEquals("かかい", text.convertTarget)
    }

    @Test
    fun moveCursorAndPrefix() {
        val text = ComposingText.fromConvertTarget("しかい")
            .moveCursor(-1)
            .prefixToCursorPosition()
        assertEquals("しか", text.convertTarget)
        assertEquals(2, text.convertTargetCursorPosition)
    }

    @Test
    fun prefixCompleteCompositeAppliesLeftBeforeRight() {
        val transducer = AzooKeyRoman2KanaTransducer.fromMap(
            mapOf("ka" to ("か" to 2), "n" to ("ん" to 1)),
        )
        val text = ComposingText.fromConvertTarget("")
            .insertRoman2KanaAtCursor("kan", transducer)
        val leftFirst = text.prefixComplete(
            ComposingCount.Composite(
                left = ComposingCount.SurfaceCount(1),
                right = ComposingCount.InputCount(1),
            ),
            transducer,
        )
        val rightFirst = text.prefixComplete(
            ComposingCount.Composite(
                left = ComposingCount.InputCount(1),
                right = ComposingCount.SurfaceCount(1),
            ),
            transducer,
        )
        assertEquals("", leftFirst.convertTarget)
        assertNotEquals(leftFirst.convertTarget, rightFirst.convertTarget)
    }

    @Test
    fun deleteForwardUsesBackwardSemantics() {
        val transducer = AzooKeyRoman2KanaTransducer.fromMap(
            mapOf(
                "ka" to ("か" to 2),
                "n" to ("ん" to 1),
                "su" to ("す" to 2),
            ),
        )
        var text = ComposingText.fromConvertTarget("")
        text = text.insertRoman2KanaAtCursor("kansu", transducer)
        assertEquals("かんす", text.convertTarget)
        text = text.moveCursor(-2).deleteForwardFromCursor(1, transducer)
        assertEquals("かす", text.convertTarget)
    }
}