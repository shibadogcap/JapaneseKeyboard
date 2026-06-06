package com.kazumaproject.markdownhelperkeyboard.ime_service.editor

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputContentInfo
import androidx.annotation.ColorInt
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

/**
 * [InputConnection] への commit / delete / composing / selection を集約する（Phase 3）。
 */
class EditorGateway(
    private val connectionProvider: () -> InputConnection?,
    private val onComposingChange: (CharSequence?) -> Unit = {},
    private val onPreEditMutation: () -> Unit = {},
    private val onCommit: () -> Unit = {},
    private val onFinishComposing: () -> Unit = {},
) {
    fun connection(): InputConnection? = connectionProvider()

    fun getTextBeforeCursor(length: Int, flags: Int = 0): CharSequence? {
        return connection()?.getTextBeforeCursor(length, flags)
    }

    fun getTextAfterCursor(length: Int, flags: Int = 0): CharSequence? {
        return connection()?.getTextAfterCursor(length, flags)
    }

    fun getSelectedText(flags: Int = 0): CharSequence? {
        return connection()?.getSelectedText(flags)
    }

    fun getCursorCapsMode(reqModes: Int): Int {
        val ic = connection() ?: return 0
        return ic.getCursorCapsMode(reqModes)
    }

    fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        return connection()?.getExtractedText(request, flags)
    }

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val ic = connection() ?: return false
        onPreEditMutation()
        return ic.deleteSurroundingText(beforeLength, afterLength)
    }

    fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        val ic = connection() ?: return false
        onPreEditMutation()
        return ic.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val ic = connection() ?: return false
        onComposingChange(text)
        return ic.setComposingText(text, newCursorPosition)
    }

    fun setComposingRegion(start: Int, end: Int): Boolean {
        val ic = connection() ?: return false
        onComposingChange(null)
        return ic.setComposingRegion(start, end)
    }

    fun finishComposingText(): Boolean {
        val ic = connection() ?: return false
        onFinishComposing()
        return ic.finishComposingText()
    }

    fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val ic = connection() ?: return false
        onCommit()
        return ic.commitText(text, newCursorPosition)
    }

    /**
     * 音声入力などの確定: [finishComposingText] と同様に hook → IC の順。commit 失敗時は false。
     */
    fun commitRecognizedText(text: String): Boolean {
        val ic = connection() ?: return false
        onFinishComposing()
        if (!ic.finishComposingText()) return false
        onCommit()
        return ic.commitText(text, 1)
    }

    /** 選択範囲をカーソル位置へ collapse（変換キャンセル hook 付き）。 */
    fun collapseSelectionToCursor(): Boolean {
        val ic = connection() ?: return false
        onPreEditMutation()
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val collapsePos = extracted.selectionEnd
        if (collapsePos < 0) return false
        return ic.setSelection(collapsePos, collapsePos)
    }

    fun setSelection(start: Int, end: Int): Boolean {
        return connection()?.setSelection(start, end) ?: false
    }

    fun sendKeyEvent(event: KeyEvent?): Boolean {
        val ic = connection() ?: return false
        return ic.sendKeyEvent(event)
    }

    fun performEditorAction(actionCode: Int): Boolean {
        return connection()?.performEditorAction(actionCode) ?: false
    }

    fun performContextMenuAction(id: Int): Boolean {
        return connection()?.performContextMenuAction(id) ?: false
    }

    fun beginBatchEdit(): Boolean = connection()?.beginBatchEdit() ?: false

    fun endBatchEdit(): Boolean = connection()?.endBatchEdit() ?: false

    fun commitCompletion(completion: CompletionInfo?): Boolean {
        return connection()?.commitCompletion(completion) ?: false
    }

    fun commitCorrection(correction: CorrectionInfo?): Boolean {
        return connection()?.commitCorrection(correction) ?: false
    }

    fun clearMetaKeyStates(states: Int): Boolean {
        return connection()?.clearMetaKeyStates(states) ?: false
    }

    fun reportFullscreenMode(enabled: Boolean): Boolean {
        return connection()?.reportFullscreenMode(enabled) ?: false
    }

    fun performPrivateCommand(action: String?, data: Bundle?): Boolean {
        return connection()?.performPrivateCommand(action, data) ?: false
    }

    fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
        return connection()?.requestCursorUpdates(cursorUpdateMode) ?: false
    }

    fun handler(): Handler? = connection()?.handler

    fun closeConnection() {
        connection()?.closeConnection()
    }

    fun commitContent(
        inputContent: InputContentInfo,
        flags: Int,
        opts: Bundle?,
    ): Boolean {
        val ic = connection() ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ic.commitContent(inputContent, flags, opts)
        } else {
            false
        }
    }

    fun commitContentCompat(
        inputContent: InputContentInfoCompat,
        flags: Int,
        opts: Bundle?,
        editorInfo: EditorInfo?,
    ): Boolean {
        val ic = connection() ?: return false
        return InputConnectionCompat.commitContent(ic, editorInfo, inputContent, flags, opts)
    }

    /**
     * 編集前（PreEdit）の装飾 composing を [setComposingText] へ委譲する。
     */
    fun setComposingTextPreEdit(
        inputString: String,
        spannableString: SpannableString,
        tailLength: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null,
    ): Boolean {
        val inputLength = inputString.length
        spannableString.apply {
            setSpan(
                BackgroundColorSpan(backgroundColor),
                0,
                inputLength,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING,
            )
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    inputLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING,
                )
            }
            setSpan(
                UnderlineSpan(),
                0,
                inputLength + tailLength,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING,
            )
        }
        return setComposingText(spannableString, 1)
    }

    /**
     * 編集後（AfterEdit）の装飾 composing を [setComposingText] へ委譲する。
     */
    fun setComposingTextAfterEdit(
        inputString: String,
        spannableString: SpannableString,
        tailLength: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null,
    ): Boolean {
        val underlineEnd = if (tailLength > 0) {
            inputString.length + tailLength
        } else {
            inputString.length
        }
        spannableString.apply {
            setSpan(
                BackgroundColorSpan(backgroundColor),
                0,
                inputString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING,
            )
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    inputString.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING,
                )
            }
            setSpan(
                UnderlineSpan(),
                0,
                underlineEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING,
            )
        }
        return setComposingText(spannableString, 1)
    }
}