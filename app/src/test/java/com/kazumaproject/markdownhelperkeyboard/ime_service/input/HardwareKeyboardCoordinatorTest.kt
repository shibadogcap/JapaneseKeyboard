package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import android.hardware.input.InputManager
import android.view.InputDevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HardwareKeyboardCoordinatorTest {
    @Test
    fun nullDeviceIsNotPhysicalKeyboard() {
        val coordinator = HardwareKeyboardCoordinator(mock())
        assertFalse(coordinator.isPhysicalKeyboard(null))
    }

    @Test
    fun virtualKeyboardIsRejected() {
        val device = mock<InputDevice>()
        whenever(device.isVirtual).thenReturn(true)
        whenever(device.isExternal).thenReturn(true)
        whenever(device.sources).thenReturn(InputDevice.SOURCE_KEYBOARD)
        whenever(device.keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        val coordinator = HardwareKeyboardCoordinator(mock())
        assertFalse(coordinator.isPhysicalKeyboard(device))
    }

    @Test
    fun alphabeticNonVirtualKeyboardIsAccepted() {
        val device = mock<InputDevice>()
        whenever(device.isVirtual).thenReturn(false)
        whenever(device.isExternal).thenReturn(true)
        whenever(device.sources).thenReturn(InputDevice.SOURCE_KEYBOARD)
        whenever(device.keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        val coordinator = HardwareKeyboardCoordinator(mock<InputManager>())
        assertTrue(coordinator.isPhysicalKeyboard(device))
    }

    @Test
    fun internalKeyboardLikeDeviceIsRejected() {
        val device = mock<InputDevice>()
        whenever(device.isVirtual).thenReturn(false)
        whenever(device.isExternal).thenReturn(false)
        whenever(device.sources).thenReturn(InputDevice.SOURCE_KEYBOARD)
        whenever(device.keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        val coordinator = HardwareKeyboardCoordinator(mock<InputManager>())
        assertFalse(coordinator.isPhysicalKeyboard(device))
    }

    @Test
    fun nonAlphabeticExternalKeyboardLikeDeviceIsRejected() {
        val device = mock<InputDevice>()
        whenever(device.isVirtual).thenReturn(false)
        whenever(device.isExternal).thenReturn(true)
        whenever(device.sources).thenReturn(InputDevice.SOURCE_KEYBOARD)
        whenever(device.keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        val coordinator = HardwareKeyboardCoordinator(mock())
        assertFalse(coordinator.isPhysicalKeyboard(device))
    }

    @Test
    fun keyboardWithoutSourceIsRejected() {
        val device = mock<InputDevice>()
        whenever(device.isVirtual).thenReturn(false)
        whenever(device.isExternal).thenReturn(true)
        whenever(device.sources).thenReturn(0)
        whenever(device.keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        val coordinator = HardwareKeyboardCoordinator(mock())
        assertFalse(coordinator.isPhysicalKeyboard(device))
    }

    @Test
    fun hasAnyPhysicalKeyboardScansDeviceIds() {
        val inputManager = mock<InputManager>()
        val physical = mock<InputDevice>()
        val other = mock<InputDevice>()
        whenever(inputManager.inputDeviceIds).thenReturn(intArrayOf(1, 2))
        whenever(inputManager.getInputDevice(1)).thenReturn(physical)
        whenever(inputManager.getInputDevice(2)).thenReturn(other)
        whenever(physical.isVirtual).thenReturn(false)
        whenever(physical.isExternal).thenReturn(true)
        whenever(physical.sources).thenReturn(InputDevice.SOURCE_KEYBOARD)
        whenever(physical.keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        whenever(other.isVirtual).thenReturn(true)
        whenever(other.isExternal).thenReturn(true)
        whenever(other.sources).thenReturn(InputDevice.SOURCE_KEYBOARD)
        whenever(other.keyboardType).thenReturn(InputDevice.KEYBOARD_TYPE_ALPHABETIC)

        val coordinator = HardwareKeyboardCoordinator(inputManager)
        assertTrue(coordinator.hasAnyPhysicalKeyboard())
        assertTrue(coordinator.isPhysicalDeviceId(1))
        assertFalse(coordinator.isPhysicalDeviceId(2))
    }

    @Test
    fun refreshPresenceNotifiesListenerWithScanResult() {
        val inputManager = mock<InputManager>()
        whenever(inputManager.inputDeviceIds).thenReturn(intArrayOf())
        val coordinator = HardwareKeyboardCoordinator(inputManager)
        var seen: Boolean? = null
        coordinator.refreshPresence { seen = it }
        assertEquals(false, seen)
    }

    @Test
    fun onDeviceAddedAlwaysRescans() {
        val inputManager = mock<InputManager>()
        whenever(inputManager.inputDeviceIds).thenReturn(intArrayOf(99))
        whenever(inputManager.getInputDevice(99)).thenReturn(null)
        val coordinator = HardwareKeyboardCoordinator(inputManager)
        var seen: Boolean? = null
        coordinator.onDeviceAdded(99) { seen = it }
        assertEquals(false, seen)
    }

    @Test
    fun shouldUseFloatingCandidateBarRequiresBothFlags() {
        val coordinator = HardwareKeyboardCoordinator(mock())
        assertTrue(coordinator.shouldUseFloatingCandidateBar(true, true))
        assertFalse(coordinator.shouldUseFloatingCandidateBar(true, false))
        assertFalse(coordinator.shouldUseFloatingCandidateBar(false, true))
    }

    @Test
    fun applyPresenceEffectInvokesHostInConnectOrder() {
        val coordinator = HardwareKeyboardCoordinator(mock())
        val events = mutableListOf<String>()
        val host = object : HardwareKeyboardCoordinator.PhysicalKeyboardPresenceHost {
            override fun clearZenzContextCache() { events += "zenz" }
            override fun dismissFloatingDock() { events += "dock" }
            override fun dismissFloatingModeSwitch() { events += "mode" }
            override fun dismissFloatingCandidate() { events += "candidate" }
            override fun setHasHardwareKeyboardConnected(connected: Boolean) {
                events += "connected:$connected"
            }
            override fun schedulePhysicalKeyboardEnableEmit(enabled: Boolean) {
                events += "emit:$enabled"
            }
            override fun setKeyboardFloatingMode(floating: Boolean) {
                events += "floating:$floating"
            }
        }
        coordinator.applyPresenceEffect(
            HardwareKeyboardCoordinator.PhysicalKeyboardPresenceEffect(
                hasPhysicalKeyboard = true,
                keyboardFloatingModeWhenDisconnected = true,
            ),
            host,
            previousHasPhysicalKeyboard = false,
        )
        assertEquals(
            listOf("zenz", "dock", "mode", "candidate", "connected:true", "emit:true", "floating:false"),
            events,
        )
    }

    @Test
    fun disconnectRestoresFloatingPreference() {
        val coordinator = HardwareKeyboardCoordinator(mock())
        var floating: Boolean? = null
        coordinator.applyPresenceEffect(
            HardwareKeyboardCoordinator.PhysicalKeyboardPresenceEffect(
                hasPhysicalKeyboard = false,
                keyboardFloatingModeWhenDisconnected = true,
            ),
            object : HardwareKeyboardCoordinator.PhysicalKeyboardPresenceHost {
                override fun clearZenzContextCache() {}
                override fun dismissFloatingDock() {}
                override fun dismissFloatingModeSwitch() {}
                override fun dismissFloatingCandidate() {}
                override fun setHasHardwareKeyboardConnected(connected: Boolean) {}
                override fun schedulePhysicalKeyboardEnableEmit(enabled: Boolean) {}
                override fun setKeyboardFloatingMode(floatingMode: Boolean) {
                    floating = floatingMode
                }
            },
            previousHasPhysicalKeyboard = true,
        )
        assertEquals(true, floating)
    }

    @Test
    fun applyPresenceEffectSkipsZenzClearWhenPresenceUnchanged() {
        val coordinator = HardwareKeyboardCoordinator(mock())
        var zenzClears = 0
        val host = object : HardwareKeyboardCoordinator.PhysicalKeyboardPresenceHost {
            override fun clearZenzContextCache() { zenzClears += 1 }
            override fun dismissFloatingDock() {}
            override fun dismissFloatingModeSwitch() {}
            override fun dismissFloatingCandidate() {}
            override fun setHasHardwareKeyboardConnected(connected: Boolean) {}
            override fun schedulePhysicalKeyboardEnableEmit(enabled: Boolean) {}
            override fun setKeyboardFloatingMode(floating: Boolean) {}
        }
        coordinator.applyPresenceEffect(
            HardwareKeyboardCoordinator.PhysicalKeyboardPresenceEffect(
                hasPhysicalKeyboard = true,
                keyboardFloatingModeWhenDisconnected = false,
            ),
            host,
            previousHasPhysicalKeyboard = true,
        )
        assertEquals(0, zenzClears)
    }

    @Test
    fun physicalKeyboardEmitDelayMillisDelaysConnectOnly() {
        val coordinator = HardwareKeyboardCoordinator(mock())
        assertEquals(32L, coordinator.physicalKeyboardEmitDelayMillis(true))
        assertEquals(0L, coordinator.physicalKeyboardEmitDelayMillis(false))
    }
}
