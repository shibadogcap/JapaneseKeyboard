package com.kazumaproject.markdownhelperkeyboard.converter.api

/**
 * AzooKey [ComposingText](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 * 変換 API の入力は plain [String] ではなくこの型を使う。
 */
data class ComposingText(
    val convertTarget: String,
    val convertTargetCursorPosition: Int = convertTarget.length,
    val input: List<InputElement> = emptyList(),
) {
    val isEmpty: Boolean
        get() = convertTarget.isEmpty()

    val isComposing: Boolean
        get() = convertTarget.isNotEmpty()

    val isAtEndIndex: Boolean
        get() = convertTargetCursorPosition == convertTarget.length

    fun inputIndexToSurfaceIndexMap(
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): Map<Int, Int> =
        ComposingTextIndexMapper.inputIndexToSurfaceIndexMap(this, roman2Kana)

    companion object {
        fun fromConvertTarget(target: String): ComposingText {
            val elements = target.map { char ->
                InputElement(
                    piece = InputPiece.Character(char),
                    inputStyle = InputStyle.Direct,
                )
            }
            return ComposingText(
                convertTarget = target,
                input = elements,
            )
        }

        fun fromInputElements(
            elements: List<InputElement>,
            roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
        ): ComposingText {
            val target = ComposingTextIndexMapper.buildConvertTarget(elements, roman2Kana)
            return ComposingText(
                convertTarget = target,
                convertTargetCursorPosition = target.length,
                input = elements,
            )
        }
    }
}

data class InputElement(
    val piece: InputPiece,
    val inputStyle: InputStyle = InputStyle.Direct,
)

sealed interface InputPiece {
    data class Character(val value: Char) : InputPiece
    data object CompositionSeparator : InputPiece
}

enum class InputStyle {
    Direct,
    Roman2Kana,
    /** カーソル移動時に input を surface へ凍結したセグメント */
    Frozen,
}