package com.kazumaproject.markdownhelperkeyboard.ime_service.state

import com.kazumaproject.core.domain.state.InputMode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 入力セッション中フラグの read-only スナップショット（IME Phase 7 スケッチ）。
 *
 * [ImeSessionState.fromImeService] は **IME メインスレッド専用**（複数フィールドの非原子的読み取り）。
 * 本番経路: [IMEService.currentImeCandidateRuntimeSession] → [buildImeCandidatePreferences]。
 */
data class ImeSessionState(
    val inputString: String,
    val stringInTail: String,
    val isHenkan: Boolean,
    val hasConvertedKatakana: Boolean,
    val currentInputMode: InputMode,
    val selectModeActive: Boolean,
    val cursorMoveModeActive: Boolean,
    val suppressSuggestions: Boolean,
) {
    companion object {
        fun fromImeService(
            inputString: String,
            stringInTail: String,
            isHenkan: AtomicBoolean,
            hasConvertedKatakana: Boolean,
            sessionInputMode: InputMode,
            selectModeActive: Boolean,
            cursorMoveModeActive: Boolean,
            suppressSuggestions: Boolean,
        ): ImeSessionState = ImeSessionState(
            inputString = inputString,
            stringInTail = stringInTail,
            isHenkan = isHenkan.get(),
            hasConvertedKatakana = hasConvertedKatakana,
            currentInputMode = sessionInputMode,
            selectModeActive = selectModeActive,
            cursorMoveModeActive = cursorMoveModeActive,
            suppressSuggestions = suppressSuggestions,
        )
    }
}