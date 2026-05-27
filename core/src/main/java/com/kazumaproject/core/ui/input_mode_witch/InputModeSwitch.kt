package com.kazumaproject.core.ui.input_mode_witch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
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

    private class CenteredDrawableSpan(
        private val drawable: Drawable
    ) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val rect = drawable.bounds
            fm?.let {
                val fontMetrics = paint.fontMetricsInt
                val fontHeight = fontMetrics.descent - fontMetrics.ascent
                val drawableHeight = rect.height()
                val centerY = fontMetrics.ascent + fontHeight / 2
                it.ascent = centerY - drawableHeight / 2
                it.descent = centerY + drawableHeight / 2
                it.top = it.ascent
                it.bottom = it.descent
            }
            return rect.width()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val rect = drawable.bounds
            val transY = top + (bottom - top - rect.height()) / 2
            canvas.save()
            canvas.translate(x, transY.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    private fun setCenteredDrawable(drawable: Drawable?) {
        setCompoundDrawables(null, null, null, null)
        if (drawable == null) {
            text = ""
            return
        }
        val spanText = SpannableString(" ")
        spanText.setSpan(CenteredDrawableSpan(drawable), 0, spanText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text = spanText
    }

    @Suppress("UNUSED_PARAMETER")
    fun setInputMode(inputMode: InputMode, isTablet: Boolean) {
        currentInputMode = inputMode
        gravity = android.view.Gravity.CENTER
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        setCompoundDrawables(null, null, null, null)
        val label = SpannableString("あa1")
        val boldRange = when (inputMode) {
            InputMode.ModeJapanese -> 0 to 1
            InputMode.ModeEnglish -> 1 to 2
            InputMode.ModeNumber -> 2 to 3
        }
        label.setSpan(
            StyleSpan(Typeface.BOLD),
            boldRange.first,
            boldRange.second,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text = label
        textSize = 13f
    }
}
