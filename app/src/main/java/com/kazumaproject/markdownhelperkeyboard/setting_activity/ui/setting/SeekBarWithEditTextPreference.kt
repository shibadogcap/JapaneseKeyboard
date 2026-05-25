package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.SeekBar
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import com.kazumaproject.markdownhelperkeyboard.R

class SeekBarWithEditTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0
) : SeekBarPreference(context, attrs, defStyleAttr, defStyleRes) {

    private var isUpdating = false

    init {
        layoutResource = R.layout.preference_seekbar_with_edittext
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val seekBar = holder.findViewById(androidx.preference.R.id.seekbar) as? SeekBar
        val editText = holder.findViewById(R.id.seekbar_value_edittext) as? EditText

        if (seekBar != null && editText != null) {
            // 初期状態の値を設定
            editText.setText(value.toString())

            // SeekBarの変更イベント
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && !isUpdating) {
                        isUpdating = true
                        val newValue = progress + min
                        editText.setText(newValue.toString())
                        value = newValue
                        isUpdating = false
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            // EditTextの確定イベント
            editText.setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    updateValueFromText(editText, seekBar)
                    editText.clearFocus()
                    true
                } else {
                    false
                }
            }

            // フォーカス喪失イベントでの確定
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    updateValueFromText(editText, seekBar)
                }
            }
        }
    }

    private fun updateValueFromText(editText: EditText, seekBar: SeekBar) {
        if (isUpdating) return
        val text = editText.text.toString()
        val parsedValue = text.toIntOrNull()
        if (parsedValue != null) {
            isUpdating = true
            // 範囲内にクランプ
            val clampedValue = parsedValue.coerceIn(min, max)
            editText.setText(clampedValue.toString())
            value = clampedValue
            seekBar.progress = clampedValue - min
            isUpdating = false
        } else {
            // 不正な数値なら元の値に戻す
            editText.setText(value.toString())
        }
    }
}
