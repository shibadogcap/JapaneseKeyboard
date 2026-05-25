package com.kazumaproject.core.ui.input_mode_witch

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import com.kazumaproject.core.R
import com.kazumaproject.core.domain.state.InputMode

class InputModeSwitch(context: Context, attrs: AttributeSet) :
    AppCompatButton(context, attrs) {

    private var currentInputMode: InputMode = InputMode.ModeJapanese

    init {
        isAllCaps = false
    }

    private fun scaleDrawable(drawable: Drawable?): Drawable? {
        if (drawable == null) return null
        val density = resources.displayMetrics.density
        val size = (24 * density).toInt() // 24dp相当のサイズにスケーリングして全体表示を保証
        drawable.setBounds(0, 0, size, size)
        return drawable
    }

    fun setInputMode(inputMode: InputMode, isTablet: Boolean) {
        currentInputMode = inputMode
        val resId = when (inputMode) {
            InputMode.ModeJapanese -> if (isTablet) R.drawable.input_mode_japanese_select_tablet else R.drawable.input_mode_japanese_select
            InputMode.ModeEnglish -> if (isTablet) R.drawable.input_mode_english_select_tablet else R.drawable.input_mode_english_select
            InputMode.ModeNumber -> if (isTablet) R.drawable.input_mode_number_select_tablet else R.drawable.input_mode_number_select
        }
        val drawable = AppCompatResources.getDrawable(context, resId)
        setCompoundDrawables(scaleDrawable(drawable), null, null, null)
        gravity = android.view.Gravity.CENTER
    }
}