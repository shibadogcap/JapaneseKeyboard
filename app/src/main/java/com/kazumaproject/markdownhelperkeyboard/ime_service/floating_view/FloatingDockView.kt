package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.LayoutFloatingDockBinding

class FloatingDockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    // View Bindingを使ってレイアウトをインフレートし、このViewにアタッチする
    private val binding: LayoutFloatingDockBinding =
        LayoutFloatingDockBinding.inflate(LayoutInflater.from(context), this, true)
    private var listener: FloatingDockListener? = null

    init {
        setupClickListeners()
    }

    /**
     * クリックリスナーを設定します。
     */
    fun setOnFloatingDockListener(listener: FloatingDockListener) {
        this.listener = listener
    }

    /**
     * TextViewに表示するテキストを設定します。
     * @param text 表示する文字列
     */
    fun setText(text: String) {
        binding.dockText.text = text
    }

    fun applyThemeColors(
        backgroundColor: Int,
        textColor: Int,
        iconBackgroundColor: Int,
    ) {
        setBackgroundColor(backgroundColor)
        binding.dockText.setTextColor(textColor)
        binding.dockIcon.setColorFilter(textColor)
        binding.dockIcon.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * resources.displayMetrics.density
            setColor(iconBackgroundColor)
        }
    }

    /**
     * クリックイベントのリスナーをセットアップします。
     */
    private fun setupClickListeners() {
        // View全体へのクリックリスナー
        this.setOnClickListener {
            listener?.onDockClick()
        }

        // ImageViewへのクリックリスナー
        binding.dockIcon.setOnClickListener {
            // View全体のクリックイベントが発火しないようにstopPropagationする
            it.cancelPendingInputEvents()
            listener?.onIconClick()
        }

    }
}
