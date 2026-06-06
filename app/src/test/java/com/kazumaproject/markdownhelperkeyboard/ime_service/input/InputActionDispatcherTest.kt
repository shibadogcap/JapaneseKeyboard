package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import android.view.KeyEvent
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class InputActionDispatcherTest {
    private val dispatcher = InputActionDispatcher()
    private val mainView: MainLayoutBinding = mock()

    @Test
    fun routesToJapaneseHandler() {
        var called = false
        val handled = dispatcher.dispatchOnKeyDown(
            currentMode = InputMode.ModeJapanese,
            keyCode = KeyEvent.KEYCODE_A,
            event = null,
            mainView = mainView,
            handlers = handlers(
                japanese = { _, _, _ ->
                    called = true
                    true
                },
            ),
        )
        assertTrue(called)
        assertTrue(handled)
    }

    @Test
    fun japaneseHandlerFalseReturnsFalse() {
        val handled = dispatcher.dispatchOnKeyDown(
            currentMode = InputMode.ModeJapanese,
            keyCode = KeyEvent.KEYCODE_A,
            event = null,
            mainView = mainView,
            handlers = handlers(japanese = { _, _, _ -> false }),
        )
        assertFalse(handled)
    }

    @Test
    fun routesToEnglishHandlerAndForwardsKeyCode() {
        var seenCode = -1
        val handled = dispatcher.dispatchOnKeyDown(
            currentMode = InputMode.ModeEnglish,
            keyCode = KeyEvent.KEYCODE_B,
            event = null,
            mainView = mainView,
            handlers = handlers(
                english = { code, _, _ ->
                    seenCode = code
                    true
                },
            ),
        )
        assertTrue(handled)
        assertEquals(KeyEvent.KEYCODE_B, seenCode)
    }

    @Test
    fun englishHandlerFalseReturnsFalse() {
        assertFalse(
            dispatcher.dispatchOnKeyDown(
                currentMode = InputMode.ModeEnglish,
                keyCode = KeyEvent.KEYCODE_C,
                event = null,
                mainView = mainView,
                handlers = handlers(english = { _, _, _ -> false }),
            ),
        )
    }

    @Test
    fun routesToNumberHandlerWithKeyEvent() {
        var seenEvent: KeyEvent? = null
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1)
        val handled = dispatcher.dispatchOnKeyDown(
            currentMode = InputMode.ModeNumber,
            keyCode = KeyEvent.KEYCODE_1,
            event = event,
            mainView = mainView,
            handlers = handlers(
                number = { _, ev, _ ->
                    seenEvent = ev
                    true
                },
            ),
        )
        assertTrue(handled)
        assertEquals(event, seenEvent)
    }

    private fun handlers(
        japanese: (Int, KeyEvent?, MainLayoutBinding) -> Boolean = { _, _, _ -> false },
        english: (Int, KeyEvent?, MainLayoutBinding) -> Boolean = { _, _, _ -> false },
        number: (Int, KeyEvent?, MainLayoutBinding) -> Boolean = { _, _, _ -> false },
    ) = InputModeKeyHandlers(
        onJapaneseKeyDown = japanese,
        onEnglishKeyDown = english,
        onNumberKeyDown = number,
    )
}