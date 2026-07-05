package com.kazumaproject.custom_keyboard.view

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

class CustomTypefaceSpan(private val typeface: Typeface, private val isBold: Boolean) : MetricAffectingSpan() {
    override fun updateDrawState(ds: TextPaint) {
        apply(ds)
    }

    override fun updateMeasureState(paint: TextPaint) {
        apply(paint)
    }

    private fun apply(paint: TextPaint) {
        val oldStyle = paint.typeface?.style ?: 0
        val want = oldStyle or if (isBold) Typeface.BOLD else Typeface.NORMAL
        
        val tf = Typeface.create(typeface, want)
        val fake = want and tf.style.inv()
        if (fake and Typeface.BOLD != 0) {
            paint.isFakeBoldText = true
        }
        if (fake and Typeface.ITALIC != 0) {
            paint.textSkewX = -0.25f
        }
        paint.typeface = tf
    }
}
