package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import android.graphics.Matrix
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.inputmethod.CursorAnchorInfo
import android.widget.PopupWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 物理キーボード検出と表示モード副作用を IME サービスから切り出す（IME Phase 5）。
 * [physicalKeyboardEnable] の解釈・emit（32ms connect debounce）と floating 候補追従はここに集約。
 * collect 内の **UI ポリシー**は [PhysicalKeyboardUiEffectHandler]、View 操作は [IMEService] host。
 *
 * 判定: 外部 + 非 virtual + [InputDevice.SOURCE_KEYBOARD] + 英字フルキーボード型。
 */
class HardwareKeyboardCoordinator(
    private val inputManager: InputManager,
) {
    fun interface PhysicalKeyboardPresenceListener {
        fun onPhysicalKeyboardPresenceChanged(hasPhysicalKeyboard: Boolean)
    }

    /**
     * [physicalKeyboardEnable] Flow と接続状態の解釈を 1 箇所に集約する。
     */
    fun shouldUseFloatingCandidateBar(
        physicalKeyboardEnableFlag: Boolean,
        hasHardwareKeyboardConnected: Boolean,
    ): Boolean {
        return physicalKeyboardEnableFlag && hasHardwareKeyboardConnected
    }

    fun shouldRouteCandidatesToFloatingBar(
        physicalKeyboardEnableReplayFirst: Boolean?,
    ): Boolean {
        return physicalKeyboardEnableReplayFirst == true
    }

    /** [PhysicalKeyboardUiEffectHandler] と同じ cursor update ポリシー。 */
    fun cursorUpdateFlagsForPhysicalKeyboardEnabled(enabled: Boolean): Int {
        return if (enabled) {
            PhysicalKeyboardUiEffectHandler.CURSOR_UPDATE_MONITORING
        } else {
            PhysicalKeyboardUiEffectHandler.CURSOR_UPDATE_DISABLED
        }
    }

    data class PhysicalKeyboardPresenceEffect(
        val hasPhysicalKeyboard: Boolean,
        val dismissFloatingWindows: Boolean = true,
        val keyboardFloatingModeWhenDisconnected: Boolean,
    )

    fun buildPresenceEffect(
        hasPhysicalKeyboard: Boolean,
        readFloatingModePreference: () -> Boolean,
    ): PhysicalKeyboardPresenceEffect {
        return PhysicalKeyboardPresenceEffect(
            hasPhysicalKeyboard = hasPhysicalKeyboard,
            keyboardFloatingModeWhenDisconnected = readFloatingModePreference(),
        )
    }

    /**
     * 接続 / 切断時の UI 副作用（Zenz キャッシュクリア・floating dismiss・モードフラグは host が適用）。
     */
    fun applyPresenceEffect(
        effect: PhysicalKeyboardPresenceEffect,
        host: PhysicalKeyboardPresenceHost,
        previousHasPhysicalKeyboard: Boolean,
    ) {
        if (previousHasPhysicalKeyboard != effect.hasPhysicalKeyboard) {
            host.clearZenzContextCache()
        }
        if (effect.dismissFloatingWindows) {
            host.dismissFloatingDock()
            host.dismissFloatingModeSwitch()
            host.dismissFloatingCandidate()
        }
        host.setHasHardwareKeyboardConnected(effect.hasPhysicalKeyboard)
        host.schedulePhysicalKeyboardEnableEmit(effect.hasPhysicalKeyboard)
        host.setKeyboardFloatingMode(
            if (effect.hasPhysicalKeyboard) {
                false
            } else {
                effect.keyboardFloatingModeWhenDisconnected
            },
        )
    }

    interface PhysicalKeyboardPresenceHost {
        fun clearZenzContextCache()
        fun dismissFloatingDock()
        fun dismissFloatingModeSwitch()
        fun dismissFloatingCandidate()
        fun setHasHardwareKeyboardConnected(connected: Boolean)
        fun schedulePhysicalKeyboardEnableEmit(enabled: Boolean)
        fun setKeyboardFloatingMode(floating: Boolean)
    }

    data class FloatingCandidateAnchorUpdate(
        val x: Int,
        val y: Int,
        val initialCursorXPosition: Int,
        val markInitialCursorDetect: Boolean,
    )

    /**
     * [onUpdateCursorAnchorInfo] から floating 候補位置を算出する（表示更新は host）。
     */
    fun resolveFloatingCandidateAnchor(
        cursorAnchorInfo: CursorAnchorInfo,
        initialCursorDetectInFloatingCandidateView: Boolean,
        initialCursorXPosition: Int,
        yOffset: Int = 0,
        screenHeight: Int = 0,
        popupWindowHeight: Int = 0,
    ): FloatingCandidateAnchorUpdate {
        val matrix: Matrix = cursorAnchorInfo.matrix
        val screenCoordsBottom = floatArrayOf(
            cursorAnchorInfo.insertionMarkerHorizontal,
            cursorAnchorInfo.insertionMarkerBottom,
        )
        matrix.mapPoints(screenCoordsBottom)
        val screenX = screenCoordsBottom[0]
        val screenYBottom = screenCoordsBottom[1]
        val x = if (initialCursorDetectInFloatingCandidateView) {
            initialCursorXPosition
        } else {
            (screenX - 64).coerceAtLeast(0f).toInt()
        }
        val y = if (screenHeight > 0 && popupWindowHeight > 0 && screenYBottom > screenHeight * 0.6f) {
            val screenCoordsTop = floatArrayOf(
                cursorAnchorInfo.insertionMarkerHorizontal,
                cursorAnchorInfo.insertionMarkerTop,
            )
            matrix.mapPoints(screenCoordsTop)
            val screenYTop = screenCoordsTop[1]
            (screenYTop.toInt() - popupWindowHeight - yOffset).coerceAtLeast(0)
        } else {
            screenYBottom.toInt() + yOffset
        }
        return FloatingCandidateAnchorUpdate(
            x = x,
            y = y,
            initialCursorXPosition = x,
            markInitialCursorDetect = true,
        )
    }

    fun shouldProcessFloatingCandidateAnchor(
        cursorAnchorInfo: CursorAnchorInfo?,
        floatingCandidateWindow: PopupWindow?,
        composingInputNonEmpty: Boolean,
    ): Boolean {
        return cursorAnchorInfo != null &&
            floatingCandidateWindow != null &&
            composingInputNonEmpty &&
            !cursorAnchorInfo.insertionMarkerHorizontal.isNaN() &&
            !cursorAnchorInfo.insertionMarkerBottom.isNaN()
    }

    fun interface FloatingCandidateWindowHost {
        fun updateFloatingCandidatePosition(x: Int, y: Int, window: PopupWindow)
    }

    fun applyFloatingCandidateAnchor(
        update: FloatingCandidateAnchorUpdate,
        window: PopupWindow,
        host: FloatingCandidateWindowHost,
    ) {
        host.updateFloatingCandidatePosition(update.x, update.y, window)
    }

    fun isPhysicalKeyboard(device: InputDevice?): Boolean {
        if (device == null) return false
        val isNotVirtual = !device.isVirtual
        val isExternal = device.isExternal
        val hasKeyboardSource = (device.sources and InputDevice.SOURCE_KEYBOARD) != 0
        val isFullKeyboard = device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        return isExternal && isNotVirtual && hasKeyboardSource && isFullKeyboard
    }

    fun hasAnyPhysicalKeyboard(): Boolean {
        return inputManager.inputDeviceIds.any { deviceId ->
            isPhysicalKeyboard(inputManager.getInputDevice(deviceId))
        }
    }

    fun getDevice(deviceId: Int): InputDevice? = inputManager.getInputDevice(deviceId)

    fun isPhysicalDeviceId(deviceId: Int): Boolean {
        return isPhysicalKeyboard(getDevice(deviceId))
    }

    fun refreshPresence(listener: PhysicalKeyboardPresenceListener) {
        listener.onPhysicalKeyboardPresenceChanged(hasAnyPhysicalKeyboard())
    }

    fun onDeviceAdded(deviceId: Int, listener: PhysicalKeyboardPresenceListener) {
        refreshPresence(listener)
    }

    fun onDeviceChanged(listener: PhysicalKeyboardPresenceListener) {
        refreshPresence(listener)
    }

    /**
     * Legacy [IMEService.checkForPhysicalKeyboard]: connect delays 32ms; disconnect is immediate.
     */
    fun physicalKeyboardEmitDelayMillis(enabled: Boolean): Long = if (enabled) 32L else 0L

    fun schedulePhysicalKeyboardEnableEmit(
        enabled: Boolean,
        scope: CoroutineScope,
        emit: suspend (Boolean) -> Unit,
    ) {
        scope.launch {
            val delayMs = physicalKeyboardEmitDelayMillis(enabled)
            if (delayMs > 0) {
                delay(delayMs)
            }
            emit(enabled)
        }
    }
}
