package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.TapFlickInputBridge
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.TenKeyGestureBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InputActionDispatcherTenKeyGestureTest {

    @Test
    fun dispatchTenKeyGestureForwardsArgumentsToBridge() {
        val recording = RecordingTenKeyGestureBridge()
        val dispatcher = InputActionDispatcher(tapFlickBridge = recording)
        val request = TapFlickInputBridge.TapFlickDispatchRequest(
            key = Key.SideKeyDelete,
            char = null,
            insertString = "ab",
            isFlick = false,
            gestureType = GestureType.Tap,
            suggestions = emptyList(),
        )
        val sb = StringBuilder("x")
        val session = NoOpSession()
        val surface = NoOpSurface()

        dispatcher.dispatchTenKeyGesture(request, sb, session, surface)

        assertSame(request, recording.request)
        assertSame(sb, recording.sb)
        assertSame(session, recording.session)
        assertSame(surface, recording.surfaceActions)
    }

    private class RecordingTenKeyGestureBridge : TenKeyGestureBridge {
        var request: TapFlickInputBridge.TapFlickDispatchRequest? = null
        var sb: StringBuilder? = null
        var session: TapFlickInputBridge.TapFlickSessionHooks? = null
        var surfaceActions: TapFlickInputBridge.TapFlickSurfaceActions? = null

        override fun dispatch(
            request: TapFlickInputBridge.TapFlickDispatchRequest,
            sb: StringBuilder,
            session: TapFlickInputBridge.TapFlickSessionHooks,
            surfaceActions: TapFlickInputBridge.TapFlickSurfaceActions,
        ) {
            this.request = request
            this.sb = sb
            this.session = session
            this.surfaceActions = surfaceActions
        }
    }

    private class NoOpSession : TapFlickInputBridge.TapFlickSessionHooks {
        override val vibrationTimingStr: String? = null
        override fun vibrate() {}
        override val selectModeActive: Boolean = false
        override val deletedBufferNotEmpty: Boolean = false
        override fun clearDeletedBuffer() {}
        override fun clearDeletedBufferWithoutResetLayout() {}
        override fun refreshEditHistoryUi() {}
        override val leftCursorLongPressed: Boolean = false
        override val rightCursorLongPressed: Boolean = false
        override val deleteKeyLongPressed: Boolean = false
        override val isHenkanActive: Boolean = false
        override val cursorMoveModeActive: Boolean = false
        override fun exitCursorMoveMode() {}
        override val isSpaceKeyLongPressed: Boolean = false
        override fun setSpaceKeyLongPressed(value: Boolean) {}
        override val hankakuPreference: Boolean? = null
        override val isDeleteLeftFlickPreference: Boolean? = null
        override val isDeleteUpFlickPreference: Boolean? = null
        override val isDeleteDownFlickPreference: Boolean? = null
        override fun onLeftKeyLongPressReleased() {}
        override fun onRightKeyLongPressReleased() {}
        override fun stopDeleteLongPress() {}
        override fun toggleSymbolKeyboard() {}
        override fun finishComposingAndClearTail() {}
        override fun performCopy() {}
        override fun performCut() {}
        override fun performSelectAll() {}
        override fun performShareSelectedText() {}
    }

    private class NoOpSurface : TapFlickInputBridge.TapFlickSurfaceActions {
        override val surface =
            com.kazumaproject.markdownhelperkeyboard.ime_service.ui.MainImeKeyboardSurface {
                com.kazumaproject.core.domain.state.InputMode.ModeJapanese
            }
        override fun handleNonEmptyEnter(insertString: String, suggestions: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>) {}
        override fun handleEmptyEnter() {}
        override fun handleDakutenSmall(sb: StringBuilder, isFlick: Boolean, char: Char?, insertString: String, gestureType: GestureType) {}
        override fun moveFocusedBunsetsu(delta: Int): Boolean = false
        override fun handleDeleteKeyInHenkan(suggestions: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>, insertString: String) {}
        override fun handleLeftCursor(gestureType: GestureType, insertString: String) {}
        override fun handleJapaneseModeSpaceKey(suggestions: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>, insertString: String) {}
        override fun actionInRightKeyPressed(gestureType: GestureType, insertString: String) {}
        override fun handleDeleteKeyTap(insertString: String, suggestions: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>) {}
        override fun deleteWordOrSymbolsBeforeCursor(insertString: String) {}
        override fun deleteWordOrSymbolsAfterCursor(insertString: String) {}
        override fun undoLastHistoryEntry() {}
        override fun setTenkeyIconsInHenkan(insertString: String) {}
        override fun setNextReturnInputCharacter(insertString: String) {}
        override fun cycleFocusedBunsetsuCandidate(delta: Int): Boolean = false
        override fun handleSpaceKeyClick(isHankaku: Boolean, insertString: String, suggestions: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>) {}
        override fun handleFlick(char: Char?, insertString: String, sb: StringBuilder) {}
        override fun handleTap(char: Char?, insertString: String, sb: StringBuilder) {}
    }
}