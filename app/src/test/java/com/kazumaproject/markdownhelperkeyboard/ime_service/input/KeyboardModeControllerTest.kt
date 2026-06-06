package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KeyboardModeControllerTest {
    @Test
    fun sessionModeDefaultsToJapanese() {
        val controller = KeyboardModeController()
        assertEquals(InputMode.ModeJapanese, controller.sessionMode)
    }

    @Test
    fun setSessionModeUpdatesDispatcherSource() {
        val controller = KeyboardModeController()
        controller.setSessionMode(InputMode.ModeEnglish)
        assertEquals(InputMode.ModeEnglish, controller.sessionMode)
    }

    @Test
    fun readTenkeyModeFromTenKeyStateFlow() {
        val tenKey = mock<TenKey>()
        val modeFlow = kotlinx.coroutines.flow.MutableStateFlow<InputMode>(InputMode.ModeNumber)
        whenever(tenKey.currentInputMode).thenReturn(modeFlow)
        val controller = KeyboardModeController()
        assertEquals(InputMode.ModeNumber, controller.readTenkeyInputMode(tenKey))
    }

    @Test
    fun resolveTenkeySurfaceModePrefersTabletWhenEnabled() {
        assertEquals(
            InputMode.ModeEnglish,
            KeyboardModeController.resolveTenkeySurfaceMode(
                useTabletGojuonSurface = true,
                tabletMode = InputMode.ModeEnglish,
                tenKeyMode = InputMode.ModeJapanese,
            ),
        )
    }

    @Test
    fun resolveTenkeySurfaceModePrefersTenKeyWhenTabletDisabled() {
        assertEquals(
            InputMode.ModeNumber,
            KeyboardModeController.resolveTenkeySurfaceMode(
                useTabletGojuonSurface = false,
                tabletMode = InputMode.ModeJapanese,
                tenKeyMode = InputMode.ModeNumber,
            ),
        )
    }

    @Test
    fun applyTenkeySurfaceModeInvokesTabletCallback() {
        var seen: InputMode? = null
        KeyboardModeController.applyTenkeySurfaceMode(
            useTabletGojuonSurface = true,
            mode = InputMode.ModeEnglish,
            onTablet = { seen = it },
            onTenKey = { seen = InputMode.ModeNumber },
        )
        assertEquals(InputMode.ModeEnglish, seen)
    }

    @Test
    fun applyTenkeySurfaceModeInvokesTenKeyCallback() {
        var seen: InputMode? = null
        KeyboardModeController.applyTenkeySurfaceMode(
            useTabletGojuonSurface = false,
            mode = InputMode.ModeNumber,
            onTablet = { seen = InputMode.ModeJapanese },
            onTenKey = { seen = it },
        )
        assertEquals(InputMode.ModeNumber, seen)
    }
}