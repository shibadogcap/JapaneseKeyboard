package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import org.junit.Assert.assertEquals
import org.junit.Test

class TapFlickInputBridgeTest {
    private val bridge = TapFlickInputBridge()

    @Test
    fun enterWithTextDelegatesToNonEmptyHandler() {
        val events = mutableListOf<String>()
        bridge.dispatch(
            request = request(key = Key.SideKeyEnter, insertString = "あ"),
            sb = StringBuilder(),
            session = recordingSession(),
            surface = recordingSurface(events, InputMode.ModeJapanese),
        )
        assertEquals(listOf("enter:nonempty"), events)
    }

    @Test
    fun spaceFlickLeftInvertsHankakuPreference() {
        val events = mutableListOf<String>()
        bridge.dispatch(
            request = request(
                key = Key.SideKeySpace,
                insertString = "a",
                gestureType = GestureType.FlickLeft,
            ),
            sb = StringBuilder(),
            session = recordingSession(hankaku = true),
            surface = recordingSurface(events, InputMode.ModeJapanese),
        )
        assertEquals(listOf("space:false"), events)
    }

    @Test
    fun mainAndFloatingProduceSameTraceForSharedSideKeyDelete() {
        val request = request(key = Key.SideKeyDelete, insertString = "txt")
        val mainEvents = mutableListOf<String>()
        val floatingEvents = mutableListOf<String>()
        bridge.dispatch(
            request = request,
            sb = StringBuilder(),
            session = recordingSession(),
            surface = recordingSurface(mainEvents, InputMode.ModeJapanese, ImeKeyboardSurface.Kind.Main),
        )
        bridge.dispatch(
            request = request,
            sb = StringBuilder(),
            session = recordingSession(),
            surface = recordingSurface(floatingEvents, InputMode.ModeJapanese, ImeKeyboardSurface.Kind.Floating),
        )
        assertEquals(mainEvents, floatingEvents)
        assertEquals(listOf("delete:tap"), mainEvents)
    }

    @Test
    fun floatingSurfaceReadsInputModeFromSurface() {
        val events = mutableListOf<String>()
        bridge.dispatch(
            request = request(key = Key.SideKeyPreviousChar, insertString = ""),
            sb = StringBuilder(),
            session = recordingSession(),
            surface = recordingSurface(
                events,
                InputMode.ModeNumber,
                ImeKeyboardSurface.Kind.Floating,
            ),
        )
        assertEquals(emptyList<String>(), events)
    }

    private fun request(
        key: Key,
        insertString: String = "",
        gestureType: GestureType = GestureType.Tap,
        isFlick: Boolean = false,
    ) = TapFlickInputBridge.TapFlickDispatchRequest(
        key = key,
        char = null,
        insertString = insertString,
        isFlick = isFlick,
        gestureType = gestureType,
        suggestions = emptyList(),
    )

    private fun recordingSession(
        hankaku: Boolean = false,
    ): TapFlickInputBridge.TapFlickSessionHooks =
        object : TapFlickInputBridge.TapFlickSessionHooks {
            override val vibrationTimingStr: String? = "press"
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
            override val hankakuPreference: Boolean? = hankaku
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

    private fun recordingSurface(
        events: MutableList<String>,
        mode: InputMode,
        kind: ImeKeyboardSurface.Kind = ImeKeyboardSurface.Kind.Main,
    ): TapFlickInputBridge.TapFlickSurfaceActions {
        val surface: ImeKeyboardSurface = when (kind) {
            ImeKeyboardSurface.Kind.Main -> MainImeKeyboardSurface { mode }
            ImeKeyboardSurface.Kind.Floating -> FloatingImeKeyboardSurface { mode }
        }
        val shared = object : TapFlickSurfaceActionsFactory.SharedHost {
            override fun handleDeleteKeyInHenkan(
                suggestions: List<Candidate>,
                insertString: String,
            ) {
            }
            override fun handleLeftCursor(gestureType: GestureType, insertString: String) {}
            override fun actionInRightKeyPressed(gestureType: GestureType, insertString: String) {}
            override fun handleDeleteKeyTap(insertString: String, suggestions: List<Candidate>) {
                events += "delete:tap"
            }
            override fun deleteWordOrSymbolsBeforeCursor(insertString: String) {}
            override fun deleteWordOrSymbolsAfterCursor(insertString: String) {}
            override fun undoLastHistoryEntry() {}
            override fun setNextReturnInputCharacter(insertString: String) {}
        }
        return TapFlickSurfaceActionsFactory.create(
            shared = shared,
            bindings = TapFlickSurfaceActionsFactory.Bindings(
                surface = surface,
                onNonEmptyEnter = { _, _ -> events += "enter:nonempty" },
                onEmptyEnter = { events += "enter:empty" },
                onDakutenSmall = { _, _, _, _, _ -> },
                moveFocusedBunsetsu = { false },
                onJapaneseModeSpaceKey = { _, _ -> },
                setTenkeyIconsInHenkan = { },
                cycleFocusedBunsetsuCandidate = { false },
                onSpaceKeyClick = { isHankaku, _, _ -> events += "space:$isHankaku" },
                onFlick = { _, _, _ -> },
                onTap = { _, _, _ -> },
            ),
        )
    }

}