package com.kazumaproject.markdownhelperkeyboard.converter.api

/**
 * AzooKey [ComposingText.inputIndexToSurfaceIndexMap](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
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
        val buffer: MutableList<Char>,
        val inputStyle: InputStyle,
        val directBuilder: StringBuilder?,
    ) {
        val string: String
            get() = directBuilder?.toString() ?: buffer.joinToString("")
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
            return appendToConvertTarget(currentElements[lastIndex], newElement.piece, roman2Kana)
        }
        when (newElement.piece) {
            is InputPiece.CompositionSeparator -> {
                currentElements += ConvertTargetElement(
                    buffer = mutableListOf(),
                    inputStyle = newElement.inputStyle,
                    directBuilder = StringBuilder(),
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
            val buffer = mutableListOf<Char>()
            roman2Kana.apply(buffer, piece.value.lowercaseChar())
            ConvertTargetElement(
                buffer = buffer,
                inputStyle = InputStyle.Roman2Kana,
                directBuilder = null,
            )
        } else {
            ConvertTargetElement(
                buffer = mutableListOf(),
                inputStyle = element.inputStyle,
                directBuilder = StringBuilder(piece.value.toString()),
            )
        }
    }

    private fun appendToConvertTarget(
        element: ConvertTargetElement,
        piece: InputPiece,
        roman2Kana: AzooKeyRoman2KanaTransducer,
    ): Int {
        return when (piece) {
            is InputPiece.CompositionSeparator -> 0
            is InputPiece.Character -> {
                if (element.inputStyle == InputStyle.Roman2Kana && element.directBuilder == null) {
                    roman2Kana.apply(element.buffer, piece.value.lowercaseChar())
                } else {
                    element.directBuilder?.append(piece.value)
                    0
                }
            }
        }
    }
}
