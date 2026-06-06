package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import android.graphics.drawable.ColorDrawable
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class KeyboardSurfaceCoordinatorTest {
    @Test
    fun japaneseHenkanWithHiraganaTailUsesKanaDrawable() {
        val tenKey = mock<TenKey>()
        val coordinator = KeyboardSurfaceCoordinator()
        val drawables = KeyboardSurfaceCoordinator.HenkanDrawableSet(
            returnDrawable = ColorDrawable(1),
            henkanDrawable = ColorDrawable(2),
            spaceDrawable = ColorDrawable(3),
            kanaDrawable = ColorDrawable(4),
            englishDrawable = ColorDrawable(5),
            numberDrawable = ColorDrawable(6),
            tenkeyShowImeButton = true,
        )
        coordinator.updateHenkanOnTenKey(
            tenKey = tenKey,
            insertString = "か",
            currentInputMode = InputMode.ModeJapanese,
            drawables = drawables,
        )
        verify(tenKey).setSideKeyEnterDrawable(drawables.returnDrawable)
        verify(tenKey).setBackgroundSmallLetterKey(drawables.kanaDrawable)
        verify(tenKey).setSideKeySpaceDrawable(drawables.henkanDrawable)
    }

    @Test
    fun englishHenkanWithoutLatinUsesLanguageToggle() {
        val tenKey = mock<TenKey>()
        val coordinator = KeyboardSurfaceCoordinator()
        val drawables = KeyboardSurfaceCoordinator.HenkanDrawableSet(
            returnDrawable = null,
            henkanDrawable = null,
            spaceDrawable = ColorDrawable(3),
            kanaDrawable = null,
            englishDrawable = null,
            numberDrawable = null,
            tenkeyShowImeButton = false,
        )
        coordinator.updateHenkanOnTenKey(
            tenKey = tenKey,
            insertString = "",
            currentInputMode = InputMode.ModeEnglish,
            drawables = drawables,
        )
        verify(tenKey).setBackgroundSmallLetterKey(isLanguageEnable = false, isEnglish = true)
        verify(tenKey).setSideKeySpaceDrawable(drawables.spaceDrawable)
    }
}