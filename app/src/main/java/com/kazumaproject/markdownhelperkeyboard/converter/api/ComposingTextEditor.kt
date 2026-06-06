package com.kazumaproject.markdownhelperkeyboard.converter.api

/**
 * AzooKey [ComposingText](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の入力編集。
 */
fun ComposingText.insertDirectAtCursor(text: String): ComposingText {
    if (text.isEmpty()) return this
    return insertAtCursor(
        text.map { char ->
            InputElement(
                piece = InputPiece.Character(char),
                inputStyle = InputStyle.Direct,
            )
        },
    )
}

fun ComposingText.insertRoman2KanaAtCursor(
    romaji: String,
    roman2Kana: AzooKeyRoman2KanaTransducer,
): ComposingText {
    if (romaji.isEmpty()) return this
    val elements = romaji.map { char ->
        InputElement(
            piece = InputPiece.Character(char),
            inputStyle = InputStyle.Roman2Kana,
        )
    }
    return insertAtCursor(elements, roman2Kana)
}

/**
 * 末尾の Roman2Kana セグメントへ 1 文字追記する（`か` + `k` + `i` + `a` → `かkai` 相当）。
 */
fun ComposingText.appendRoman2KanaCharAtEnd(
    char: Char,
    roman2Kana: AzooKeyRoman2KanaTransducer,
): ComposingText {
    if (!isAtEndIndex) {
        return insertRoman2KanaAtCursor(char.toString(), roman2Kana)
    }
    val romanStart = trailingRoman2KanaStartIndex()
    val prefix = input.take(romanStart)
    val romaji = buildString {
        input.drop(romanStart).forEach { element ->
            val piece = element.piece
            if (element.inputStyle == InputStyle.Roman2Kana && piece is InputPiece.Character) {
                append(piece.value)
            }
        }
        append(char)
    }
    val romanElements = romaji.map { romajiChar ->
        InputElement(
            piece = InputPiece.Character(romajiChar),
            inputStyle = InputStyle.Roman2Kana,
        )
    }
    return ComposingText.fromInputElements(prefix + romanElements, roman2Kana)
}

private fun ComposingText.trailingRoman2KanaStartIndex(): Int {
    var index = input.size
    while (index > 0) {
        val element = input[index - 1]
        if (element.inputStyle == InputStyle.Roman2Kana &&
            element.piece is InputPiece.Character
        ) {
            index--
        } else {
            break
        }
    }
    return index
}

fun ComposingText.insertAtCursor(
    elements: List<InputElement>,
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): ComposingText {
    if (elements.isEmpty()) return this
    val cursor = convertTargetCursorPosition.coerceIn(0, convertTarget.length)
    val inputCursor = inputIndexAtSurface(cursor, roman2Kana)
    val insertElements = if (!isAtEndIndex) {
        elements + InputElement(
            piece = InputPiece.CompositionSeparator,
            inputStyle = InputStyle.Frozen,
        )
    } else {
        elements
    }
    val trimmedInput = input.take(inputCursor) + insertElements + input.drop(inputCursor)
    return ComposingText.fromInputElements(trimmedInput, roman2Kana).copy(
        convertTargetCursorPosition = cursor +
            ComposingTextIndexMapper.buildConvertTarget(insertElements, roman2Kana).length,
    )
}

private fun ComposingText.inputIndexAtSurface(
    surfaceIndex: Int,
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): Int {
    if (surfaceIndex <= 0) return 0
    val map = inputIndexToSurfaceIndexMap(roman2Kana)
    return map.entries
        .filter { it.value <= surfaceIndex }
        .maxByOrNull { it.value }
        ?.key
        ?: input.size
}

fun ComposingText.deleteBackwardFromCursor(
    count: Int = 1,
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): ComposingText {
    val deleteCount = count.coerceAtLeast(0).coerceAtMost(convertTargetCursorPosition)
    if (deleteCount == 0) return this
    val newCursor = convertTargetCursorPosition - deleteCount
    val inputIndex = inputIndexAtSurface(newCursor, roman2Kana)
    val prefixInput = input.take(inputIndex)
    return ComposingText.fromInputElements(prefixInput, roman2Kana).copy(
        convertTargetCursorPosition = newCursor,
    )
}

fun ComposingText.deleteForwardFromCursor(count: Int = 1): ComposingText {
    val remaining = convertTarget.length - convertTargetCursorPosition
    val deleteCount = count.coerceAtLeast(0).coerceAtMost(remaining)
    if (deleteCount == 0) return this
    val before = convertTarget.substring(0, convertTargetCursorPosition)
    val after = convertTarget.substring(convertTargetCursorPosition + deleteCount)
    return copy(
        convertTarget = before + after,
        input = input,
    )
}

fun ComposingText.moveCursor(offset: Int): ComposingText {
    val newPosition = (convertTargetCursorPosition + offset)
        .coerceIn(0, convertTarget.length)
    return copy(convertTargetCursorPosition = newPosition)
}

fun ComposingText.prefixToCursorPosition(
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): ComposingText {
    val cursor = convertTargetCursorPosition.coerceIn(0, convertTarget.length)
    val inputIndex = inputIndexAtSurface(cursor, roman2Kana)
    val prefixInput = input.take(inputIndex)
    return ComposingText.fromInputElements(prefixInput, roman2Kana)
}