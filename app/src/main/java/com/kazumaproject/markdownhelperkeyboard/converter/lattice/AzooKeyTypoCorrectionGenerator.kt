package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingTextIndexMapper
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputElement
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputPiece
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyClassicTypoCorrection

/**
 * AzooKey [TypoCorrectionGenerator](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の Kotlin port。
 */
class AzooKeyTypoCorrectionGenerator(
    private val inputs: List<InputElement>,
    private val range: ProcessRange,
    private val roman2Kana: AzooKeyRoman2KanaTransducer,
) {
    data class ProcessRange(
        val leftIndex: Int,
        val rightRangeStart: Int,
        val rightRangeEndExclusive: Int,
    ) {
        val rightIndexRange: IntRange get() = rightRangeStart until rightRangeEndExclusive
        val count: Int get() = rightIndexRange.count()
    }

    data class TypoReading(
        val katakana: String,
        val penalty: Float,
        val endIndex: com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIndex,
    )

    private data class TypoCandidate(
        val inputElements: List<InputElement>,
        val weight: Float,
    )

    private data class StackEntry(
        val convertTarget: String,
        val elementCount: Int,
        val penalty: Float,
    )

    private val maxPenalty = 3.5f * 3f
    private val count = range.count
    private val nodes: List<List<TypoCandidate>>
    private val stack: ArrayDeque<StackEntry>

    init {
        nodes = (0 until count).map { i ->
            LENGTHS.flatMap { k ->
                val j = i + k
                if (count <= j) emptyList() else getTypo(inputs.subList(range.leftIndex + i, range.leftIndex + j + 1))
            }
        }
        stack = ArrayDeque(
            nodes[0].map { candidate ->
                val convertTarget = ComposingTextIndexMapper.buildConvertTarget(
                    candidate.inputElements,
                    roman2Kana,
                )
                StackEntry(
                    convertTarget = convertTarget,
                    elementCount = candidate.inputElements.size,
                    penalty = candidate.weight,
                )
            },
        )
    }

    fun setUnreachablePath(target: String) {
        if (target.isEmpty()) return
        val targetKatakana = target.hiraganaToKatakana()
        val filtered = stack.filterNot { entry ->
            val stablePrefix = entry.convertTarget.hiraganaToKatakana()
            var matched = 0
            for (ch in stablePrefix) {
                if (matched >= targetKatakana.length) break
                if (ch != targetKatakana[matched]) {
                    return@filterNot false
                }
                matched++
                if (matched >= targetKatakana.length) {
                    return@filterNot true
                }
            }
            false
        }
        stack.clear()
        stack.addAll(filtered)
    }

    fun next(): TypoReading? {
        while (stack.isNotEmpty()) {
            val entry = stack.removeLast()
            var yield: TypoReading? = null
            val endInputIndex = range.leftIndex + entry.elementCount - 1
            if (endInputIndex in range.rightIndexRange) {
                val katakana = entry.convertTarget.hiraganaToKatakana()
                if (entry.penalty > 0f) {
                    yield = TypoReading(
                        katakana = katakana,
                        penalty = entry.penalty,
                        endIndex = AzooKeyLatticeIndex.Input(endInputIndex),
                    )
                }
            }
            if (entry.elementCount >= nodes.size) {
                yield?.let { return it }
                continue
            }
            if (entry.penalty >= maxPenalty) {
                val correctIndex = range.leftIndex + entry.elementCount
                if (correctIndex < inputs.size) {
                    val correct = inputs[correctIndex]
                    val extended = entry.convertTarget + ComposingTextIndexMapper.buildConvertTarget(
                        listOf(correct),
                        roman2Kana,
                    )
                    stack.addLast(
                        StackEntry(
                            convertTarget = extended,
                            elementCount = entry.elementCount + 1,
                            penalty = entry.penalty,
                        ),
                    )
                }
            } else {
                for (node in nodes[entry.elementCount]) {
                    if (entry.elementCount + node.inputElements.size > nodes.size) continue
                    val extended = entry.convertTarget + ComposingTextIndexMapper.buildConvertTarget(
                        node.inputElements,
                        roman2Kana,
                    )
                    stack.addLast(
                        StackEntry(
                            convertTarget = extended,
                            elementCount = entry.elementCount + node.inputElements.size,
                            penalty = entry.penalty + node.weight,
                        ),
                    )
                }
            }
            yield?.let { return it }
        }
        return null
    }

    companion object {
        private val LENGTHS = listOf(0, 1)

        fun collectTypoReadings(
            composingText: ComposingText,
            surfaceStart: Int,
            surfaceEndExclusive: Int,
            roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
        ): List<TypoReading> {
            val segmentLen = surfaceEndExclusive - surfaceStart
            if (segmentLen <= 2) return emptyList()
            val segment = composingText.convertTarget
                .substring(surfaceStart, surfaceEndExclusive)
                .hiraganaToKatakana()

            if (AzooKeyClassicTypoCorrection.isDirectOnlyInput(composingText.input)) {
                return generateKatakanaTypoReadings(segment)
            }

            val inputRange = inputRangeForSurface(
                composingText = composingText,
                surfaceStart = surfaceStart,
                surfaceEndExclusive = surfaceEndExclusive,
            ) ?: return generateKatakanaTypoReadings(segment)

            val generator = AzooKeyTypoCorrectionGenerator(
                inputs = composingText.input,
                range = inputRange,
                roman2Kana = roman2Kana,
            )
            val results = linkedMapOf<String, Float>()
            while (true) {
                val next = generator.next() ?: break
                val existing = results[next.katakana]
                if (existing == null || next.penalty < existing) {
                    results[next.katakana] = next.penalty
                }
            }
            if (results.isNotEmpty()) {
                return results.map { (katakana, penalty) ->
                    TypoReading(
                        katakana = katakana,
                        penalty = penalty,
                        endIndex = AzooKeyLatticeIndex.Surface(katakana.length - 1),
                    )
                }
            }
            return generateKatakanaTypoReadings(segment)
        }

        fun inputRangeForSurface(
            composingText: ComposingText,
            surfaceStart: Int,
            surfaceEndExclusive: Int,
            maxLength: Int = 20,
        ): ProcessRange? {
            val inputToSurface = ComposingTextIndexMapper.inputIndexToSurfaceIndexMap(composingText)
            val inputStart = inputToSurface.entries
                .filter { it.value <= surfaceStart }
                .maxByOrNull { it.value }
                ?.key
                ?: surfaceStart.coerceAtMost(composingText.input.size)
            val inputEnd = inputToSurface.entries
                .filter { it.value < surfaceEndExclusive }
                .maxByOrNull { it.value }
                ?.key
                ?.plus(1)
                ?: minOf(surfaceEndExclusive, composingText.input.size)
            val right = minOf(inputEnd, inputStart + maxLength, composingText.input.size)
            if (inputStart >= right) return null
            return ProcessRange(
                leftIndex = inputStart,
                rightRangeStart = inputStart,
                rightRangeEndExclusive = right,
            )
        }

        internal fun generateKatakanaTypoReadings(katakana: String): List<TypoReading> {
            if (katakana.length <= 2) return emptyList()
            val bestPenalty = linkedMapOf<String, Float>()

            fun record(path: String, penalty: Float) {
                if (penalty <= 0f || path.isEmpty()) return
                val existing = bestPenalty[path]
                if (existing == null || penalty < existing) {
                    bestPenalty[path] = penalty
                }
            }

            fun dfs(index: Int, penalty: Float, buffer: StringBuilder) {
                if (index >= katakana.length) {
                    record(buffer.toString(), penalty)
                    return
                }
                if (penalty >= 3.5f * 3f) {
                    buffer.append(katakana[index])
                    dfs(index + 1, penalty, buffer)
                    buffer.setLength(buffer.length - 1)
                    return
                }
                buffer.append(katakana[index])
                dfs(index + 1, penalty, buffer)
                buffer.setLength(buffer.length - 1)
                for (variant in AzooKeyClassicTypoCorrection.directTypoVariants(katakana[index].toString())) {
                    val nextPenalty = penalty + variant.weight
                    buffer.append(variant.katakana)
                    dfs(index + 1, nextPenalty, buffer)
                    buffer.setLength(buffer.length - 1)
                }
            }

            dfs(index = 0, penalty = 0f, buffer = StringBuilder())
            return bestPenalty.entries.map { (reading, penalty) ->
                TypoReading(
                    katakana = reading,
                    penalty = penalty.coerceAtLeast(1f),
                    endIndex = AzooKeyLatticeIndex.Surface(reading.length - 1),
                )
            }
        }

        private fun getTypo(elements: List<InputElement>): List<TypoCandidate> {
            if (elements.isEmpty()) return emptyList()
            val key = elements.joinToString("") { element ->
                when (val piece = element.piece) {
                    is InputPiece.Character -> piece.value.toString().hiraganaToKatakana()
                    InputPiece.CompositionSeparator -> ""
                }
            }
            val styleType = elements.fold(InputStylesType.None) { result, element ->
                when (result) {
                    InputStylesType.Other -> InputStylesType.Other
                    InputStylesType.OnlyDirect -> if (element.inputStyle == InputStyle.Direct) {
                        InputStylesType.OnlyDirect
                    } else {
                        InputStylesType.Other
                    }
                    InputStylesType.OnlyRoman2Kana -> when (element.inputStyle) {
                        InputStyle.Roman2Kana -> InputStylesType.OnlyRoman2Kana
                        InputStyle.Direct -> InputStylesType.Other
                        else -> InputStylesType.Other
                    }
                    InputStylesType.None -> when (element.inputStyle) {
                        InputStyle.Direct -> InputStylesType.OnlyDirect
                        InputStyle.Roman2Kana -> InputStylesType.OnlyRoman2Kana
                        else -> InputStylesType.Other
                    }
                }
            }
            return when (styleType) {
                InputStylesType.OnlyDirect -> {
                    val variants = AzooKeyClassicTypoCorrection.directTypoVariants(key).map { unit ->
                        TypoCandidate(
                            inputElements = unit.katakana.map {
                                InputElement(InputPiece.Character(it), InputStyle.Direct)
                            },
                            weight = unit.weight,
                        )
                    }
                    if (key.length == 1) {
                        variants + TypoCandidate(
                            inputElements = elements,
                            weight = 0f,
                        )
                    } else {
                        variants
                    }
                }
                InputStylesType.OnlyRoman2Kana -> {
                    val romanKey = elements.joinToString("") { element ->
                        when (val piece = element.piece) {
                            is InputPiece.Character -> piece.value.lowercase()
                            InputPiece.CompositionSeparator -> ""
                        }
                    }
                    val variants = AzooKeyClassicTypoCorrection.roman2KanaTypoVariants(romanKey).map { alt ->
                        TypoCandidate(
                            inputElements = alt.map {
                                InputElement(InputPiece.Character(it), InputStyle.Roman2Kana)
                            },
                            weight = 3.5f,
                        )
                    }
                    if (romanKey.length == 1) {
                        variants + TypoCandidate(
                            inputElements = romanKey.map {
                                InputElement(InputPiece.Character(it), InputStyle.Roman2Kana)
                            },
                            weight = 0f,
                        )
                    } else {
                        variants
                    }
                }
                InputStylesType.None, InputStylesType.Other -> if (elements.size == 1) {
                    listOf(TypoCandidate(inputElements = elements, weight = 0f))
                } else {
                    emptyList()
                }
            }
        }

        private enum class InputStylesType {
            None,
            OnlyDirect,
            OnlyRoman2Kana,
            Other,
        }
    }
}
