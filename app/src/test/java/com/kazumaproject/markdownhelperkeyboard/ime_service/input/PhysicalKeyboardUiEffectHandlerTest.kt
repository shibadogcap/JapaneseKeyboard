package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import android.view.inputmethod.InputConnection
import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardUiEffectHandlerTest {
    private val handler = PhysicalKeyboardUiEffectHandler()

    @Test
    fun enabledEffectUsesMonitoringCursorUpdates() {
        val effect = handler.buildUiEffect(
            physicalKeyboardEnabled = true,
            sessionInputMode = InputMode.ModeJapanese,
        )
        assertEquals("あ", effect.dockInputModeLabel)
        assertEquals(
            InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR,
            effect.cursorUpdateFlags,
        )
        assertEquals(1f, effect.mainRootAlpha, 0f)
        assertEquals(0, effect.windowAnimations)
        assertTrue(effect.showFloatingDock)
        assertTrue(effect.resetHenkanState)
    }

    @Test
    fun disabledEffectClearsCursorMonitoring() {
        val effect = handler.buildUiEffect(
            physicalKeyboardEnabled = false,
            sessionInputMode = InputMode.ModeEnglish,
        )
        assertEquals(0, effect.cursorUpdateFlags)
        assertEquals(1f, effect.mainRootAlpha, 0f)
        assertEquals(android.R.style.Animation_InputMethod, effect.windowAnimations)
        assertTrue(effect.dismissFloatingCandidate)
        assertTrue(effect.dismissFloatingDock)
    }

    @Test
    fun applyEffectInvokesHostInLegacyOrder() {
        val events = mutableListOf<String>()
        val host = object : PhysicalKeyboardUiEffectHandler.PhysicalKeyboardUiHost {
            override fun setWindowAnimations(animations: Int) { events += "anim:$animations" }
            override fun clearWindowBackgroundBlur() { events += "blur" }
            override fun setDockInputModeLabel(label: String) { events += "dock:$label" }
            override fun dismissFloatingKeyboard() { events += "kb" }
            override fun releaseFloatingKeyboardBackgroundVideo() { events += "video" }
            override fun expandMainRootToScreenHeight() { events += "expand" }
            override fun setMainRootAlpha(alpha: Float) { events += "alpha:$alpha" }
            override fun requestCursorUpdates(flags: Int) { events += "cursor:$flags" }
            override fun showFloatingDockIfNeeded() { events += "dockShow" }
            override fun resetCandidateHighlight() { events += "highlight" }
            override fun resetHenkanFlags() { events += "henkan" }
            override fun resizeKeyboardForPhysicalKeyboardDisconnect() { events += "resize" }
            override fun dismissFloatingCandidateWindow() { events += "fc" }
            override fun dismissFloatingDockWindow() { events += "fd" }
        }
        handler.applyUiEffect(
            handler.buildUiEffect(true, InputMode.ModeJapanese),
            host,
        )
        assertEquals(
            listOf(
                "anim:0",
                "blur",
                "dock:あ",
                "kb",
                "video",
                "expand",
                "alpha:1.0",
                "cursor:${PhysicalKeyboardUiEffectHandler.CURSOR_UPDATE_MONITORING}",
                "dockShow",
                "highlight",
                "henkan",
            ),
            events,
        )
    }

    @Test
    fun applyEffectInvokesHostInLegacyOrderOnDisconnect() {
        val events = mutableListOf<String>()
        val host = object : PhysicalKeyboardUiEffectHandler.PhysicalKeyboardUiHost {
            override fun setWindowAnimations(animations: Int) { events += "anim:$animations" }
            override fun clearWindowBackgroundBlur() {}
            override fun setDockInputModeLabel(label: String) {}
            override fun dismissFloatingKeyboard() {}
            override fun releaseFloatingKeyboardBackgroundVideo() {}
            override fun expandMainRootToScreenHeight() {}
            override fun setMainRootAlpha(alpha: Float) { events += "alpha:$alpha" }
            override fun requestCursorUpdates(flags: Int) { events += "cursor:$flags" }
            override fun showFloatingDockIfNeeded() {}
            override fun resetCandidateHighlight() {}
            override fun resetHenkanFlags() {}
            override fun resizeKeyboardForPhysicalKeyboardDisconnect() { events += "resize" }
            override fun dismissFloatingCandidateWindow() { events += "fc" }
            override fun dismissFloatingDockWindow() { events += "fd" }
        }
        handler.applyUiEffect(
            handler.buildUiEffect(false, InputMode.ModeEnglish),
            host,
        )
        assertEquals(
            listOf(
                "anim:${android.R.style.Animation_InputMethod}",
                "alpha:1.0",
                "cursor:0",
                "resize",
                "fc",
                "fd",
            ),
            events,
        )
    }
}