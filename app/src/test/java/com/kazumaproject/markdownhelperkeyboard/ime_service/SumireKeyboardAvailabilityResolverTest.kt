package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SumireKeyboardAvailabilityResolverTest {
    @Test
    fun isSumireKeyboardEnabled_returnsTrueWhenSumireIsInOrder() {
        val order = listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE, KeyboardType.QWERTY)
        assertTrue(SumireKeyboardAvailabilityResolver.isSumireKeyboardEnabled(order))
    }

    @Test
    fun isSumireKeyboardEnabled_returnsFalseWhenSumireIsRemoved() {
        val order = listOf(KeyboardType.TENKEY, KeyboardType.QWERTY)
        assertFalse(SumireKeyboardAvailabilityResolver.isSumireKeyboardEnabled(order))
    }

    @Test
    fun resolveTenKeyQWERTYModeIfSumireDisabled_fallsBackToFirstNonCustomKeyboard() {
        val order = listOf(KeyboardType.QWERTY, KeyboardType.TENKEY)

        assertEquals(
            TenKeyQWERTYMode.TenKeyQWERTY,
            SumireKeyboardAvailabilityResolver.resolveTenKeyQWERTYModeIfSumireDisabled(
                TenKeyQWERTYMode.Sumire,
                order,
            )
        )
    }

    @Test
    fun resolveTenKeyQWERTYModeIfSumireDisabled_keepsSumireWhenEnabled() {
        val order = listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE)

        assertEquals(
            TenKeyQWERTYMode.Sumire,
            SumireKeyboardAvailabilityResolver.resolveTenKeyQWERTYModeIfSumireDisabled(
                TenKeyQWERTYMode.Sumire,
                order,
            )
        )
    }

    @Test
    fun resolvePreviousTenKeyQWERTYModeIfSumireDisabled_clearsSumirePreviousMode() {
        assertNull(
            SumireKeyboardAvailabilityResolver.resolvePreviousTenKeyQWERTYModeIfSumireDisabled(
                TenKeyQWERTYMode.Sumire,
                listOf(KeyboardType.TENKEY),
            )
        )
    }

    @Test
    fun migrateFromDisabledSumireIfNeeded_migratesCurrentAndPreviousModes() {
        val result = SumireKeyboardAvailabilityResolver.migrateFromDisabledSumireIfNeeded(
            keyboardOrder = listOf(KeyboardType.TENKEY, KeyboardType.QWERTY),
            currentMode = TenKeyQWERTYMode.Sumire,
            previousMode = TenKeyQWERTYMode.Sumire,
        )

        assertTrue(result.changed)
        assertEquals(TenKeyQWERTYMode.Default, result.currentMode)
        assertNull(result.previousMode)
    }

    @Test
    fun migrateFromDisabledSumireIfNeeded_keepsNumberModeWhenSumireDisabled() {
        val result = SumireKeyboardAvailabilityResolver.migrateFromDisabledSumireIfNeeded(
            keyboardOrder = listOf(KeyboardType.TENKEY),
            currentMode = TenKeyQWERTYMode.Number,
            previousMode = TenKeyQWERTYMode.Sumire,
        )

        assertTrue(result.changed)
        assertEquals(TenKeyQWERTYMode.Number, result.currentMode)
        assertNull(result.previousMode)
    }
}
