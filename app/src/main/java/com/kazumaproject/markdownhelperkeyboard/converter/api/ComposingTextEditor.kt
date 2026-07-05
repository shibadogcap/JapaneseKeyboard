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
    val (text, forcedCursor) = forceGetInputCursorPosition(convertTargetCursorPosition, roman2Kana)
    val insertElements = if (!text.isAtEndIndex) {
        elements + InputElement(
            piece = InputPiece.CompositionSeparator,
            inputStyle = InputStyle.Frozen,
        )
    } else {
        elements
    }
    var inputCursorPosition = forcedCursor
    val mutableInput = text.input.toMutableList()
    val leftInputIndex = inputCursorPosition - 1
    if (leftInputIndex >= 0 && leftInputIndex < mutableInput.size) {
        val leftElement = mutableInput[leftInputIndex]
        if (leftElement.piece is InputPiece.CompositionSeparator &&
            leftElement.inputStyle == InputStyle.Frozen
        ) {
            mutableInput.removeAt(leftInputIndex)
            inputCursorPosition = leftInputIndex
        }
    }
    val prefixInput = mutableInput.take(inputCursorPosition)
    val suffixInput = mutableInput.drop(inputCursorPosition)
    val newInput = prefixInput + insertElements + suffixInput

    val oldConvertTargetPrefix = text.convertTarget.substring(0, text.convertTargetCursorPosition)
    val newConvertTargetPrefix = ComposingTextIndexMapper.buildConvertTarget(
        prefixInput + insertElements,
        roman2Kana,
    )
    val common = oldConvertTargetPrefix.commonPrefixWith(newConvertTargetPrefix)
    val deleted = oldConvertTargetPrefix.length - common.length
    val added = newConvertTargetPrefix.length - common.length

    return ComposingText.fromInputElements(newInput, roman2Kana).copy(
        convertTargetCursorPosition = text.convertTargetCursorPosition - deleted + added,
    )
}

/**
 * AzooKey [forceGetInputCursorPosition](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 * Roman2Kana セグメント内の surface 位置に対応する input index を返し、必要なら frozen 置換を行う。
 */
fun ComposingText.forceGetInputCursorPosition(
    targetSurfaceIndex: Int,
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): Pair<ComposingText, Int> {
    if (targetSurfaceIndex <= 0) return this to 0
    if (targetSurfaceIndex >= convertTarget.length) return this to input.size

    val boundaries = ComposingTextIndexMapper.independentSegmentBoundaries(input, roman2Kana).toMutableList()
    var cursorSegmentEnd = ComposingTextIndexMapper.IndexPair(input.size, convertTarget.length)
    var cursorSegmentStart = ComposingTextIndexMapper.IndexPair(0, 0)

    while (boundaries.isNotEmpty()) {
        val independentStart = boundaries.removeAt(boundaries.lastIndex)
        when {
            independentStart.surfaceIndex == targetSurfaceIndex -> {
                return this to independentStart.inputIndex
            }
            independentStart.surfaceIndex < targetSurfaceIndex -> {
                cursorSegmentStart = independentStart
                break
            }
            independentStart.surfaceIndex < cursorSegmentEnd.surfaceIndex -> {
                cursorSegmentEnd = independentStart
            }
        }
    }

    val cursorSegmentConvertedChars = convertTarget.substring(
        cursorSegmentStart.surfaceIndex,
        cursorSegmentEnd.surfaceIndex,
    )
    val frozenElements = cursorSegmentConvertedChars.map { char ->
        InputElement(
            piece = InputPiece.Character(char),
            inputStyle = InputStyle.Frozen,
        )
    }
    val newInput = input.take(cursorSegmentStart.inputIndex) +
        frozenElements +
        input.drop(cursorSegmentEnd.inputIndex)
    val inputIndex = targetSurfaceIndex - cursorSegmentStart.surfaceIndex + cursorSegmentStart.inputIndex
    return copy(input = newInput) to inputIndex
}

fun ComposingText.deleteBackwardFromCursor(
    count: Int = 1,
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): ComposingText {
    val deleteCount = count.coerceAtLeast(0).coerceAtMost(convertTargetCursorPosition)
    if (deleteCount == 0) return this

    var text = this
    val (textAfterTarget, targetCursorPosition) = text.forceGetInputCursorPosition(
        text.convertTargetCursorPosition - deleteCount,
        roman2Kana,
    )
    text = textAfterTarget
    val (textAfterInput, inputCursorPosition) = text.forceGetInputCursorPosition(
        text.convertTargetCursorPosition,
        roman2Kana,
    )
    text = textAfterInput

    var targetPos = targetCursorPosition
    var inputPos = inputCursorPosition
    var newInput = text.input.toMutableList()

    val targetCursorPositionLeft = targetPos - 1
    if (targetCursorPositionLeft >= 0 && targetCursorPositionLeft < newInput.size) {
        val leftElement = newInput[targetCursorPositionLeft]
        if (leftElement.piece is InputPiece.CompositionSeparator &&
            leftElement.inputStyle == InputStyle.Frozen
        ) {
            newInput.removeAt(targetCursorPositionLeft)
            targetPos = targetCursorPositionLeft
            inputPos -= 1
        }
    }

    newInput = if (targetPos == 0 || inputPos == newInput.size) {
        (newInput.take(targetPos) + newInput.drop(inputPos)).toMutableList()
    } else {
        val result = newInput.take(targetPos).toMutableList()
        result.add(
            InputElement(
                piece = InputPiece.CompositionSeparator,
                inputStyle = InputStyle.Frozen,
            ),
        )
        result.addAll(newInput.drop(inputPos))
        result
    }

    val newTarget = ComposingTextIndexMapper.buildConvertTarget(newInput, roman2Kana)
    return ComposingText(
        convertTarget = newTarget,
        convertTargetCursorPosition = text.convertTargetCursorPosition - deleteCount,
        input = newInput,
    )
}

fun ComposingText.deleteForwardFromCursor(
    count: Int = 1,
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): ComposingText {
    val remaining = convertTarget.length - convertTargetCursorPosition
    val deleteCount = count.coerceAtLeast(0).coerceAtMost(remaining)
    if (deleteCount == 0) return this
    return copy(convertTargetCursorPosition = convertTargetCursorPosition + deleteCount)
        .deleteBackwardFromCursor(deleteCount, roman2Kana)
}

fun ComposingText.moveCursor(offset: Int): ComposingText {
    val newPosition = (convertTargetCursorPosition + offset)
        .coerceIn(0, convertTarget.length)
    return copy(convertTargetCursorPosition = newPosition)
}

fun ComposingText.prefixToCursorPosition(
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): ComposingText {
    val (text, index) = forceGetInputCursorPosition(convertTargetCursorPosition, roman2Kana)
    return text.copy(
        input = text.input.take(index),
        convertTarget = text.convertTarget.take(text.convertTargetCursorPosition),
    )
}

/**
 * AzooKey [prefixComplete(composingCount:)](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
fun ComposingText.prefixComplete(
    composingCount: ComposingCount,
    roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
): ComposingText {
    return when (composingCount) {
        is ComposingCount.InputCount -> {
            val dropCount = composingCount.count.coerceAtMost(input.size)
            val remainingInput = input.drop(dropCount)
            val newTarget = ComposingTextIndexMapper.buildConvertTarget(remainingInput, roman2Kana)
            val cursorDelta = convertTarget.length - newTarget.length
            ComposingText(
                convertTarget = newTarget,
                convertTargetCursorPosition = (convertTargetCursorPosition - cursorDelta).let {
                    if (it == 0) newTarget.length else it
                },
                input = remainingInput,
            )
        }
        is ComposingCount.SurfaceCount -> {
            val dropCount = composingCount.count.coerceAtMost(convertTarget.length)
            val (text, inputIndex) = forceGetInputCursorPosition(dropCount, roman2Kana)
            val remainingInput = text.input.drop(inputIndex)
            val newTarget = text.convertTarget.drop(dropCount)
            ComposingText(
                convertTarget = newTarget,
                convertTargetCursorPosition = (text.convertTargetCursorPosition - dropCount).let {
                    if (it == 0) newTarget.length else it
                },
                input = remainingInput,
            )
        }
        is ComposingCount.Composite ->
            prefixComplete(composingCount.left, roman2Kana)
                .prefixComplete(composingCount.right, roman2Kana)
    }
}
