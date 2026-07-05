package com.kazumaproject.markdownhelperkeyboard.ime_service.input

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.tenkey.TenKey

/**
 * セッション入力モードと TenKey 表面モードの参照（IME Phase 6）。
 * [InputActionDispatcher] の [dispatchOnKeyDown] は [sessionMode] を使う。
 */
class KeyboardModeController(
    initialSessionMode: InputMode = InputMode.ModeJapanese,
) {
    var sessionMode: InputMode = initialSessionMode
        private set

    fun setSessionMode(mode: InputMode) {
        sessionMode = mode
    }

    fun readTenkeyInputMode(
        mainView: MainLayoutBinding,
        useTabletGojuonSurface: Boolean,
    ): InputMode {
        return resolveTenkeySurfaceMode(
            useTabletGojuonSurface = useTabletGojuonSurface,
            tabletMode = mainView.tabletView.currentInputMode.get(),
            tenKeyMode = mainView.keyboardView.currentInputMode.value,
        )
    }

    fun readTenkeyInputMode(tenKey: TenKey): InputMode = tenKey.currentInputMode.value

    fun syncTenkeyInputMode(
        mainView: MainLayoutBinding,
        useTabletGojuonSurface: Boolean,
        mode: InputMode,
    ) {
        applyTenkeySurfaceMode(
            useTabletGojuonSurface = useTabletGojuonSurface,
            mode = mode,
            onTablet = { mainView.tabletView.currentInputMode.set(it) },
            onTenKey = { mainView.keyboardView.setCurrentMode(it) },
        )
    }

    internal companion object {
        /** TenKey / タブレット五十字のどちらからモードを読むか（単体テスト可能）。 */
        fun resolveTenkeySurfaceMode(
            useTabletGojuonSurface: Boolean,
            tabletMode: InputMode,
            tenKeyMode: InputMode,
        ): InputMode = if (useTabletGojuonSurface) tabletMode else tenKeyMode

        /** モード同期先 surface の選択（単体テスト可能）。 */
        fun applyTenkeySurfaceMode(
            useTabletGojuonSurface: Boolean,
            mode: InputMode,
            onTablet: (InputMode) -> Unit,
            onTenKey: (InputMode) -> Unit,
        ) {
            if (useTabletGojuonSurface) {
                onTablet(mode)
            } else {
                onTenKey(mode)
            }
        }
    }
}