package com.kazumaproject.markdownhelperkeyboard.ime_service.editor

import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EditorGatewayTest {
    @Test
    fun setComposingTextNotifiesHooksAndDelegates() {
        var composingSeen: CharSequence? = null
        val connection = mock<InputConnection>()
        whenever(connection.setComposingText("あ", 1)).thenReturn(true)
        val gateway = EditorGateway(
            connectionProvider = { connection },
            onComposingChange = { composingSeen = it },
        )

        assertTrue(gateway.setComposingText("あ", 1))
        assertEquals("あ", composingSeen)
        verify(connection).setComposingText("あ", 1)
    }

    @Test
    fun commitTextRunsCommitHooks() {
        var commitCount = 0
        val connection = mock<InputConnection>()
        whenever(connection.commitText("確定", 1)).thenReturn(true)
        val gateway = EditorGateway(
            connectionProvider = { connection },
            onCommit = { commitCount += 1 },
        )

        assertTrue(gateway.commitText("確定", 1))
        assertEquals(1, commitCount)
    }

    @Test
    fun commitRecognizedTextRunsFinishHookBeforeIcFinishAndBailsOnFinishFailure() {
        var finishCount = 0
        var commitCount = 0
        val connection = mock<InputConnection>()
        whenever(connection.finishComposingText()).thenReturn(false)
        val gateway = EditorGateway(
            connectionProvider = { connection },
            onFinishComposing = { finishCount += 1 },
            onCommit = { commitCount += 1 },
        )

        assertFalse(gateway.commitRecognizedText("音声"))
        assertEquals(1, finishCount)
        assertEquals(0, commitCount)
        verify(connection).finishComposingText()
    }

    @Test
    fun commitRecognizedTextRunsHooksBeforeIcLikeFinishComposingText() {
        var finishCount = 0
        var commitCount = 0
        val connection = mock<InputConnection>()
        whenever(connection.finishComposingText()).thenReturn(true)
        whenever(connection.commitText("音声", 1)).thenReturn(true)
        val gateway = EditorGateway(
            connectionProvider = { connection },
            onFinishComposing = { finishCount += 1 },
            onCommit = { commitCount += 1 },
        )

        assertTrue(gateway.commitRecognizedText("音声"))
        assertEquals(1, finishCount)
        assertEquals(1, commitCount)
        inOrder(connection) {
            verify(connection).finishComposingText()
            verify(connection).commitText("音声", 1)
        }
    }

    @Test
    fun commitRecognizedTextReturnsFalseWhenCommitTextFailsAfterHooks() {
        var commitCount = 0
        val connection = mock<InputConnection>()
        whenever(connection.finishComposingText()).thenReturn(true)
        whenever(connection.commitText("音声", 1)).thenReturn(false)
        val gateway = EditorGateway(
            connectionProvider = { connection },
            onCommit = { commitCount += 1 },
        )

        assertFalse(gateway.commitRecognizedText("音声"))
        assertEquals(1, commitCount)
    }

    @Test
    fun setComposingRegionClearsComposingWithoutPreEditHook() {
        var preEditCount = 0
        var composingSeen: CharSequence? = "unset"
        val connection = mock<InputConnection>()
        whenever(connection.setComposingRegion(0, 2)).thenReturn(true)
        val gateway = EditorGateway(
            connectionProvider = { connection },
            onPreEditMutation = { preEditCount += 1 },
            onComposingChange = { composingSeen = it },
        )

        assertTrue(gateway.setComposingRegion(0, 2))
        assertEquals(0, preEditCount)
        assertNull(composingSeen)
    }

    @Test
    fun deleteSurroundingTextInvokesPreEditHook() {
        var preEditCount = 0
        val connection = mock<InputConnection>()
        whenever(connection.deleteSurroundingText(1, 0)).thenReturn(true)
        val gateway = EditorGateway(
            connectionProvider = { connection },
            onPreEditMutation = { preEditCount += 1 },
        )

        assertTrue(gateway.deleteSurroundingText(1, 0))
        assertEquals(1, preEditCount)
    }

    @Test
    fun sendKeyEventDelegatesToConnection() {
        val connection = mock<InputConnection>()
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        whenever(connection.sendKeyEvent(event)).thenReturn(true)
        val gateway = EditorGateway(connectionProvider = { connection })
        assertTrue(gateway.sendKeyEvent(event))
    }

    @Test
    fun readPathsReturnNullWhenConnectionMissing() {
        val gateway = EditorGateway(connectionProvider = { null })
        assertNull(gateway.getTextBeforeCursor(4))
        assertNull(gateway.getSelectedText())
        assertNull(gateway.getExtractedText(null, 0))
    }

    @Test
    fun returnsFalseWhenConnectionMissing() {
        val gateway = EditorGateway(connectionProvider = { null })
        assertFalse(gateway.setComposingText("x", 1))
        assertFalse(gateway.deleteSurroundingText(1, 0))
        assertFalse(gateway.commitRecognizedText("x"))
        assertFalse(gateway.collapseSelectionToCursor())
    }

    @Test
    fun commitCompletionDelegatesToConnection() {
        val connection = mock<InputConnection>()
        whenever(connection.commitCompletion(any())).thenReturn(true)
        val gateway = EditorGateway(connectionProvider = { connection })
        assertTrue(gateway.commitCompletion(mock()))
    }

    @Test
    fun requestCursorUpdatesDelegatesToConnection() {
        val connection = mock<InputConnection>()
        whenever(connection.requestCursorUpdates(1)).thenReturn(true)
        val gateway = EditorGateway(connectionProvider = { connection })
        assertTrue(gateway.requestCursorUpdates(1))
    }

    @Test
    fun collapseSelectionToCursorUsesExtractedSelectionEnd() {
        var preEditCount = 0
        val connection = mock<InputConnection>()
        val extracted = ExtractedText().apply { selectionEnd = 7 }
        whenever(connection.getExtractedText(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(extracted)
        whenever(connection.setSelection(7, 7)).thenReturn(true)
        val gateway = EditorGateway(
            connectionProvider = { connection },
            onPreEditMutation = { preEditCount += 1 },
        )
        assertTrue(gateway.collapseSelectionToCursor())
        assertEquals(1, preEditCount)
        verify(connection).setSelection(7, 7)
    }
}