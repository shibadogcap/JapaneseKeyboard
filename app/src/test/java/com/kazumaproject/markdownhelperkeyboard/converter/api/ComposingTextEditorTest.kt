package com.kazumaproject.markdownhelperkeyboard.converter.api

import org.junit.Assert.assertEquals
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
}