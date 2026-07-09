package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

internal object SumireKeyboardAvailabilityResolver {
    fun isSumireKeyboardEnabled(keyboardOrder: List<KeyboardType>): Boolean {
        return KeyboardType.SUMIRE in keyboardOrder
    }

    fun fallbackKeyboardType(keyboardOrder: List<KeyboardType>): KeyboardType {
        return keyboardOrder.firstOrNull { it != KeyboardType.CUSTOM } ?: KeyboardType.TENKEY
    }

    fun tenKeyQWERTYModeForKeyboardType(keyboardType: KeyboardType): TenKeyQWERTYMode {
        return when (keyboardType) {
            KeyboardType.TENKEY -> TenKeyQWERTYMode.Default
            KeyboardType.SUMIRE -> TenKeyQWERTYMode.Sumire
            KeyboardType.QWERTY -> TenKeyQWERTYMode.TenKeyQWERTY
            KeyboardType.ROMAJI -> TenKeyQWERTYMode.TenKeyQWERTYRomaji
            KeyboardType.CUSTOM -> TenKeyQWERTYMode.Custom
        }
    }

    fun resolveTenKeyQWERTYModeIfSumireDisabled(
        mode: TenKeyQWERTYMode,
        keyboardOrder: List<KeyboardType>,
    ): TenKeyQWERTYMode {
        if (mode != TenKeyQWERTYMode.Sumire || isSumireKeyboardEnabled(keyboardOrder)) {
            return mode
        }
        return tenKeyQWERTYModeForKeyboardType(fallbackKeyboardType(keyboardOrder))
    }

    fun resolvePreviousTenKeyQWERTYModeIfSumireDisabled(
        previousMode: TenKeyQWERTYMode?,
        keyboardOrder: List<KeyboardType>,
    ): TenKeyQWERTYMode? {
        if (previousMode != TenKeyQWERTYMode.Sumire || isSumireKeyboardEnabled(keyboardOrder)) {
            return previousMode
        }
        return null
    }

    fun migrateFromDisabledSumireIfNeeded(
        keyboardOrder: List<KeyboardType>,
        currentMode: TenKeyQWERTYMode,
        previousMode: TenKeyQWERTYMode?,
    ): MigrationResult {
        if (isSumireKeyboardEnabled(keyboardOrder)) {
            return MigrationResult(
                currentMode = currentMode,
                previousMode = previousMode,
                changed = false,
            )
        }

        val resolvedCurrent = resolveTenKeyQWERTYModeIfSumireDisabled(currentMode, keyboardOrder)
        val resolvedPrevious = resolvePreviousTenKeyQWERTYModeIfSumireDisabled(previousMode, keyboardOrder)
        return MigrationResult(
            currentMode = resolvedCurrent,
            previousMode = resolvedPrevious,
            changed = resolvedCurrent != currentMode || resolvedPrevious != previousMode,
        )
    }

    data class MigrationResult(
        val currentMode: TenKeyQWERTYMode,
        val previousMode: TenKeyQWERTYMode?,
        val changed: Boolean,
    )
}
