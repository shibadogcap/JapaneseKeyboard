package com.kazumaproject.markdownhelperkeyboard.converter.api

/**
 * AzooKey [ComposingText.inputIndexToSurfaceIndexMap](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 * input index と convertTarget (surface) index の対応を独立セグメント境界から構築する。
 */
object ComposingTextIndexMapper {
    internal data class IndexPair(
        val inputIndex: Int,
        val surfaceIndex: Int,
    )

    fun inputIndexToSurfaceIndexMap(
        text: ComposingText,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): Map<Int, Int> {
        val boundaries = independentSegmentBoundaries(text.input, roman2Kana)
        return boundaries.associate { it.inputIndex to it.surfaceIndex }
    }

    internal fun independentSegmentBoundaries(
        input: List<InputElement>,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): List<IndexPair> {
        if (input.isEmpty()) {
            return listOf(IndexPair(inputIndex = 0, surfaceIndex = 0))
        }
        val boundaries = mutableListOf(IndexPair(inputIndex = 0, surfaceIndex = 0))
        val converting = mutableListOf<ConvertTargetElement>()
        var convertedLength = 0

        input.forEachIndexed { currentInputIndex, element ->
            val deletedCount = updateConvertTargetElements(converting, element, roman2Kana)
            val previousConvertedLength = convertedLength
            convertedLength = converting.sumOf { it.string.length }

            while (boundaries.isNotEmpty()) {
                val last = boundaries.removeAt(boundaries.lastIndex)
                if (last.surfaceIndex <= previousConvertedLength - deletedCount) {
                    boundaries.add(last)
                    break
                }
            }
            boundaries.add(
                IndexPair(
                    inputIndex = currentInputIndex + 1,
                    surfaceIndex = convertedLength,
                ),
            )
        }
        return boundaries
    }

    fun buildConvertTarget(
        input: List<InputElement>,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): String {
        val elements = mutableListOf<ConvertTargetElement>()
        input.forEach { element ->
            updateConvertTargetElements(elements, element, roman2Kana)
        }
        return elements.joinToString(separator = "") { it.string }
    }

    private class ConvertTargetElement(
        val builder: StringBuilder,
        val romajiBuffer: StringBuilder?,
        val inputStyle: InputStyle,
        private val roman2Kana: AzooKeyRoman2KanaTransducer,
    ) {
        val string: String
            get() = when {
                inputStyle == InputStyle.Roman2Kana && romajiBuffer != null ->
                    roman2Kana.convert(romajiBuffer.toString())
                else -> builder.toString()
            }
    }

    private fun updateConvertTargetElements(
        currentElements: MutableList<ConvertTargetElement>,
        newElement: InputElement,
        roman2Kana: AzooKeyRoman2KanaTransducer,
    ): Int {
        if (currentElements.isEmpty()) {
            when (newElement.piece) {
                is InputPiece.CompositionSeparator -> return 0
                is InputPiece.Character -> {
                    currentElements += newSegmentElement(newElement, roman2Kana)
                }
            }
            return 0
        }
        val lastIndex = currentElements.lastIndex
        if (currentElements[lastIndex].inputStyle == newElement.inputStyle) {
            return appendToConvertTarget(currentElements[lastIndex], newElement.piece)
        }
        when (newElement.piece) {
            is InputPiece.CompositionSeparator -> {
                currentElements += ConvertTargetElement(
                    builder = StringBuilder(),
                    romajiBuffer = null,
                    inputStyle = newElement.inputStyle,
                    roman2Kana = roman2Kana,
                )
            }
            is InputPiece.Character -> {
                currentElements += newSegmentElement(newElement, roman2Kana)
            }
        }
        return 0
    }

    private fun newSegmentElement(
        element: InputElement,
        roman2Kana: AzooKeyRoman2KanaTransducer,
    ): ConvertTargetElement {
        val piece = element.piece as InputPiece.Character
        return if (element.inputStyle == InputStyle.Roman2Kana) {
            ConvertTargetElement(
                builder = StringBuilder(),
                romajiBuffer = StringBuilder(piece.value.lowercase()),
                inputStyle = InputStyle.Roman2Kana,
                roman2Kana = roman2Kana,
            )
        } else {
            ConvertTargetElement(
                builder = StringBuilder(piece.value.toString()),
                romajiBuffer = null,
                inputStyle = element.inputStyle,
                roman2Kana = roman2Kana,
            )
        }
    }

    private fun appendToConvertTarget(
        element: ConvertTargetElement,
        piece: InputPiece,
    ): Int {
        return when (piece) {
            is InputPiece.CompositionSeparator -> 0
            is InputPiece.Character -> {
                if (element.inputStyle == InputStyle.Roman2Kana && element.romajiBuffer != null) {
                    element.romajiBuffer.append(piece.value.lowercase())
                } else {
                    element.builder.append(piece.value)
                }
                0
            }
        }
    }
}