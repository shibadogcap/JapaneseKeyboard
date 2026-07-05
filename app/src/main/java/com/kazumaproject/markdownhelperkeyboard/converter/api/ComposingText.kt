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

    /**
     * AzooKey [differenceSuffix(to:)](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
     */
    fun differenceSuffix(previous: ComposingText): DifferenceSuffix {
        val commonInput = input.commonPrefixSize(previous.input)
        val deletedInput = previous.input.size - commonInput
        val addedInput = input.size - commonInput

        val commonSurface = convertTarget.commonPrefixWith(previous.convertTarget)
        val deletedSurface = previous.convertTarget.length - commonSurface.length
        val addedSurface = convertTarget.length - commonSurface.length

        return DifferenceSuffix(
            deletedInput = deletedInput,
            addedInput = addedInput,
            deletedSurface = deletedSurface,
            addedSurface = addedSurface,
        )
    }

    /** AzooKey [inputHasSuffix(inputOf:)](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
    fun inputHasSuffix(suffix: ComposingText): Boolean {
        if (suffix.input.size > input.size) return false
        return input.takeLast(suffix.input.size) == suffix.input
    }

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

data class DifferenceSuffix(
    val deletedInput: Int,
    val addedInput: Int,
    val deletedSurface: Int,
    val addedSurface: Int,
)

private fun List<InputElement>.commonPrefixSize(other: List<InputElement>): Int {
    val limit = minOf(size, other.size)
    for (index in 0 until limit) {
        if (this[index] != other[index]) return index
    }
    return limit
}

private fun String.commonPrefixWith(other: String): String {
    val limit = minOf(length, other.length)
    for (index in 0 until limit) {
        if (this[index] != other[index]) {
            return substring(0, index)
        }
    }
    return substring(0, limit)
}