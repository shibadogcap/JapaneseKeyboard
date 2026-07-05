package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import android.view.KeyEvent
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.DefaultTenKeyGestureBridge
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.TapFlickInputBridge
import com.kazumaproject.markdownhelperkeyboard.ime_service.ui.TenKeyGestureBridge

/**
 * 物理キー [onKeyDown] と TenKey ジェスチャのモード別振り分け（Phase 6）。
 *
 * ショートカット修飾（Ctrl 等）は [KeyEvent] 非 null のときのみ [IMEService] 側で先に処理する。
 * 各 `handle*KeyDown` は未処理キーに対して内部で `super.onKeyDown` へフォールバックする（dispatcher は boolean のみ返す）。
 */
class InputActionDispatcher(
    private val modeController: KeyboardModeController = KeyboardModeController(),
    private val tapFlickBridge: TenKeyGestureBridge = DefaultTenKeyGestureBridge(),
) {
    val keyboardMode: KeyboardModeController get() = modeController

    fun dispatchOnKeyDown(
        keyCode: Int,
        event: KeyEvent?,
        mainView: MainLayoutBinding,
        handlers: InputModeKeyHandlers,
    ): Boolean {
        return dispatchOnKeyDown(
            currentMode = modeController.sessionMode,
            keyCode = keyCode,
            event = event,
            mainView = mainView,
            handlers = handlers,
        )
    }

    fun dispatchOnKeyDown(
        currentMode: InputMode,
        keyCode: Int,
        event: KeyEvent?,
        mainView: MainLayoutBinding,
        handlers: InputModeKeyHandlers,
    ): Boolean {
        return when (currentMode) {
            InputMode.ModeJapanese -> handlers.onJapaneseKeyDown(keyCode, event, mainView)
            InputMode.ModeEnglish -> handlers.onEnglishKeyDown(keyCode, event, mainView)
            InputMode.ModeNumber -> handlers.onNumberKeyDown(keyCode, event, mainView)
        }
    }

    /**
     * 日本語 TenKey の tap/flick を [surfaceActions.surface] 経由で bridge へ渡す（Phase 4b + 6）。
     */
    fun dispatchTenKeyGesture(
        request: TapFlickInputBridge.TapFlickDispatchRequest,
        sb: StringBuilder,
        session: TapFlickInputBridge.TapFlickSessionHooks,
        surfaceActions: TapFlickInputBridge.TapFlickSurfaceActions,
    ) {
        tapFlickBridge.dispatch(request, sb, session, surfaceActions)
    }

}

data class InputModeKeyHandlers(
    val onJapaneseKeyDown: (Int, KeyEvent?, MainLayoutBinding) -> Boolean,
    val onEnglishKeyDown: (Int, KeyEvent?, MainLayoutBinding) -> Boolean,
    val onNumberKeyDown: (Int, KeyEvent?, MainLayoutBinding) -> Boolean,
)