package com.kazumaproject.core.domain.extensions

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.ColorInt

/**
 * KeyboardDrawableFactory - キーボードのキー背景Drawableを動的に生成するファクトリクラス
 * 
 * 設定値に応じて以下のスタイルをサポート：
 * - 枠線あり（角立ちキー）: 通常8-12dp角丸
 * - 枠線なし（ピル型キー）: 32dp角丸
 * - 特殊キー（確定・言語切替など）: 常にピル型（999dp角丸）
 */
object KeyboardDrawableFactory {

    /**
     * 通常キーの背景Drawableを生成
     * 
     * @param cornerRadiusDp 角丸半径（dp）
     * @param borderWidthPx 枠線幅（px）
     * @param borderColor 枠線色
     * @param fillColor 背景色
     * @param pressedFillColor プレス時の背景色
     * @return StateListDrawable（通常状態とプレス状態を持つ）
     */
    fun createKeyBackground(
        cornerRadiusDp: Float,
        borderWidthPx: Float,
        @ColorInt borderColor: Int,
        @ColorInt fillColor: Int,
        @ColorInt pressedFillColor: Int
    ): StateListDrawable {
        val stateList = StateListDrawable()

        // プレス状態
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp
            if (borderWidthPx > 0) {
                setStroke(borderWidthPx.toInt(), borderColor)
            }
            setColor(pressedFillColor)
        }
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)

        // 通常状態
        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp
            if (borderWidthPx > 0) {
                setStroke(borderWidthPx.toInt(), borderColor)
            }
            setColor(fillColor)
        }
        stateList.addState(intArrayOf(), normalDrawable)

        return stateList
    }

    /**
     * 特殊キー（確定・言語切替など）の背景Drawableを生成
     * 特殊キーは枠線有無に関わらず常にピル型（999dp角丸）
     * 
     * @param fillColor 背景色
     * @param pressedFillColor プレス時の背景色
     * @return StateListDrawable（常にピル型）
     */
    fun createSpecialKeyBackground(
        @ColorInt fillColor: Int,
        @ColorInt pressedFillColor: Int
    ): StateListDrawable {
        val stateList = StateListDrawable()

        // プレス状態（常にピル型）
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f // ピル型
            setColor(pressedFillColor)
        }
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)

        // 通常状態（常にピル型）
        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f // ピル型
            setColor(fillColor)
        }
        stateList.addState(intArrayOf(), normalDrawable)

        return stateList
    }

    /**
     * ポップアップ背景Drawableを生成
     * 
     * @param style ポップアップスタイル（"default", "pill", "circle"）
     * @param fillColor 背景色
     * @param borderWidthPx 枠線幅（px）
     * @param borderColor 枠線色
     * @return GradientDrawable
     */
    fun createPopupBackground(
        style: String,
        @ColorInt fillColor: Int,
        borderWidthPx: Float = 0f,
        @ColorInt borderColor: Int = android.graphics.Color.TRANSPARENT
    ): GradientDrawable {
        val cornerRadius = when (style) {
            "pill" -> 999f
            "circle" -> 999f
            "default" -> 8f
            else -> 8f
        }

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            if (borderWidthPx > 0) {
                setStroke(borderWidthPx.toInt(), borderColor)
            }
            setColor(fillColor)
        }
    }

    /**
     * 角丸半径をdpからpxに変換
     */
    fun dpToPx(dp: Float, density: Float): Float {
        return dp * density
    }

    /**
     * 枠線有無に基づいたデフォルトの角丸半径を返す
     * 
     * @param borderEnabled 枠線が有効かどうか
     * @param customRadiusDp カスタム角丸半径（dp）
     * @return 角丸半径（dp）
     */
    fun getDefaultCornerRadius(borderEnabled: Boolean, customRadiusDp: Int?): Float {
        return when {
            customRadiusDp != null -> customRadiusDp.toFloat()
            borderEnabled -> 8f // 枠線あり時はデフォルト8dp
            else -> 32f // 枠線なし時はデフォルト32dp（ピル型に近い）
        }
    }
}
