package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import android.view.inputmethod.InputConnection
import com.kazumaproject.core.domain.state.InputMode

/**
 * [physicalKeyboardEnable.collect] 内 UI 副作用のポリシー集約（IME Phase 5）。
 * [HardwareKeyboardCoordinator] の Flow 解釈と組み合わせて使う。
 */
class PhysicalKeyboardUiEffectHandler {

    data class PhysicalKeyboardUiEffect(
        val physicalKeyboardEnabled: Boolean,
        val dockInputModeLabel: String,
        val cursorUpdateFlags: Int,
        val mainRootAlpha: Float,
        val showFloatingDock: Boolean,
        val dismissFloatingCandidate: Boolean,
        val dismissFloatingDock: Boolean,
        val resizeMainForPhysicalKeyboard: Boolean,
        val resetCandidateHighlight: Boolean,
        val resetHenkanState: Boolean,
    )

    fun buildUiEffect(
        physicalKeyboardEnabled: Boolean,
        sessionInputMode: InputMode,
    ): PhysicalKeyboardUiEffect {
        return if (physicalKeyboardEnabled) {
            PhysicalKeyboardUiEffect(
                physicalKeyboardEnabled = true,
                dockInputModeLabel = dockLabelForMode(sessionInputMode),
                cursorUpdateFlags = CURSOR_UPDATE_MONITORING,
                mainRootAlpha = 1f,
                showFloatingDock = true,
                dismissFloatingCandidate = false,
                dismissFloatingDock = false,
                resizeMainForPhysicalKeyboard = false,
                resetCandidateHighlight = true,
                resetHenkanState = true,
            )
        } else {
            PhysicalKeyboardUiEffect(
                physicalKeyboardEnabled = false,
                dockInputModeLabel = "",
                cursorUpdateFlags = CURSOR_UPDATE_DISABLED,
                mainRootAlpha = 1f,
                showFloatingDock = false,
                dismissFloatingCandidate = true,
                dismissFloatingDock = true,
                resizeMainForPhysicalKeyboard = true,
                resetCandidateHighlight = false,
                resetHenkanState = false,
            )
        }
    }

    fun dockLabelForMode(mode: InputMode): String = when (mode) {
        InputMode.ModeJapanese -> "あ"
        InputMode.ModeEnglish,
        InputMode.ModeNumber,
        -> "A"
    }

    /**
     * Legacy collect ブロックと同じ適用順序（async 副作用の前に状態リセット、表示は host 順序通り）。
     */
    fun applyUiEffect(effect: PhysicalKeyboardUiEffect, host: PhysicalKeyboardUiHost) {
        if (effect.physicalKeyboardEnabled) {
            host.clearWindowBackgroundBlur()
            host.setDockInputModeLabel(effect.dockInputModeLabel)
            host.dismissFloatingKeyboard()
            host.releaseFloatingKeyboardBackgroundVideo()
            host.expandMainRootToScreenHeight()
            host.setMainRootAlpha(effect.mainRootAlpha)
            host.requestCursorUpdates(effect.cursorUpdateFlags)
            if (effect.showFloatingDock) {
                host.showFloatingDockIfNeeded()
            }
            if (effect.resetCandidateHighlight) {
                host.resetCandidateHighlight()
            }
            if (effect.resetHenkanState) {
                host.resetHenkanFlags()
            }
        } else {
            host.setMainRootAlpha(effect.mainRootAlpha)
            host.requestCursorUpdates(effect.cursorUpdateFlags)
            if (effect.resizeMainForPhysicalKeyboard) {
                host.resizeKeyboardForPhysicalKeyboardDisconnect()
            }
            if (effect.dismissFloatingCandidate) {
                host.dismissFloatingCandidateWindow()
            }
            if (effect.dismissFloatingDock) {
                host.dismissFloatingDockWindow()
            }
        }
    }

    interface PhysicalKeyboardUiHost {
        fun clearWindowBackgroundBlur()
        fun setDockInputModeLabel(label: String)
        fun dismissFloatingKeyboard()
        fun releaseFloatingKeyboardBackgroundVideo()
        fun expandMainRootToScreenHeight()
        fun setMainRootAlpha(alpha: Float)
        fun requestCursorUpdates(flags: Int)
        fun showFloatingDockIfNeeded()
        fun resetCandidateHighlight()
        fun resetHenkanFlags()
        fun resizeKeyboardForPhysicalKeyboardDisconnect()
        fun dismissFloatingCandidateWindow()
        fun dismissFloatingDockWindow()
    }

    companion object {
        const val CURSOR_UPDATE_MONITORING: Int =
            InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR
        const val CURSOR_UPDATE_DISABLED: Int = 0
    }
}