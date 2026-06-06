package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import com.kazumaproject.core.domain.state.InputMode

/**
 * 通常 / フローティング TenKey 表面の差分（IME Phase 4b）。
 * ロジックは [TapFlickInputBridge] / [KeyboardSurfaceCoordinator] に集約し、描画・モード参照だけ surface に残す。
 */
sealed interface ImeKeyboardSurface {
    fun currentTenkeyInputMode(): InputMode

    enum class Kind {
        Main,
        Floating,
    }

    val kind: Kind
}

data class MainImeKeyboardSurface(
    private val readTenkeyInputMode: () -> InputMode,
) : ImeKeyboardSurface {
    override val kind: ImeKeyboardSurface.Kind = ImeKeyboardSurface.Kind.Main
    override fun currentTenkeyInputMode(): InputMode = readTenkeyInputMode()
}

data class FloatingImeKeyboardSurface(
    private val readTenkeyInputMode: () -> InputMode,
) : ImeKeyboardSurface {
    override val kind: ImeKeyboardSurface.Kind = ImeKeyboardSurface.Kind.Floating
    override fun currentTenkeyInputMode(): InputMode = readTenkeyInputMode()
}

/**
 * 候補リストの main suggestion bar vs floating candidate bar への振り分け（Phase 4b）。
 */
data class CandidateSurfaceDisplayRequest(
    val routeToFloatingBar: Boolean,
    val suppressSuggestions: Boolean,
)

data class CandidateSurfaceDisplayResult(
    val routedToFloating: Boolean,
    val updatedMainSuggestions: Boolean,
)