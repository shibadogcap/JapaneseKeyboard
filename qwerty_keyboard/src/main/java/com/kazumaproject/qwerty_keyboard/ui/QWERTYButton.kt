package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import kotlin.math.min

class QWERTYButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    private val gestureDetector = GestureDetector(context, GestureListener())

    /**
     * ✅ STEP 1: 右上の文字を保持するプロパティを追加
     * このプロパティに文字を設定すると、自動的にビューが再描画されます。
     */
    var topRightChar: Char? = null
        set(value) {
            field = value
            invalidate() // Viewの再描画をリクエストする
        }

    var bottomRightChar: Char? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * ✅ STEP 2: 文字描画用のPaintオブジェクトを準備
     */
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
        textAlign = Paint.Align.RIGHT
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            8.5f,
            context.resources.displayMetrics
        )
    }
    private val textBounds = Rect()

    init {
        isAllCaps = false
    }

    fun setOverlayTypeface(typeface: Typeface?) {
        overlayPaint.typeface = typeface
        invalidate()
    }

    fun setOverlayTextColor(color: Int) {
        overlayPaint.color = color
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // gestureDetectorの処理を優先させたい場合は、super.onTouchEventより前に置く
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    /**
     * ✅ STEP 3: onDrawメソッドをオーバーライドして文字を描画
     */
    override fun onDraw(canvas: Canvas) {
        // 最初にボタン本来の描画処理を呼び出す
        super.onDraw(canvas)

        drawOverlayChar(canvas, topRightChar, top = true)
        drawOverlayChar(canvas, bottomRightChar, top = false)
    }

    private fun drawOverlayChar(canvas: Canvas, char: Char?, top: Boolean) {
        val text = char?.toString() ?: return
        val horizontalInset = (width * 0.14f).coerceAtLeast(4f)
        val verticalInset = (height * 0.10f).coerceAtLeast(3f)
        val maxTextSize = min(width, height) * 0.28f
        overlayPaint.textSize = overlayPaint.textSize.coerceAtMost(maxTextSize)
        overlayPaint.getTextBounds(text, 0, text.length, textBounds)
        val x = width - horizontalInset
        val y = if (top) {
            verticalInset - textBounds.top
        } else {
            height - verticalInset - textBounds.bottom
        }
        canvas.drawText(text, x, y, overlayPaint)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            return true
        }

        override fun onLongPress(e: MotionEvent) {
        }
    }
}
