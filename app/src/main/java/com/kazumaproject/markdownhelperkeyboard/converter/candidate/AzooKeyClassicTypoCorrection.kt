package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputElement
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputPiece
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLoudsTypoSearcher

/**
 * AzooKey [TypoCorrectionGenerator](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の
 * classic typo 辞書（direct / roman2kana）を LOUDS typo 検索に接続する。
 */
object AzooKeyClassicTypoCorrection {
    data class TypoVariant(
        val reading: String,
        val penalty: Float,
    )

    private data class TypoUnit(
        val value: String,
        val weight: Float = 3.5f,
    )

    data class DirectTypoVariant(
        val katakana: String,
        val weight: Float,
    )

    fun directTypoVariants(katakanaChar: String): List<DirectTypoVariant> {
        val normalized = katakanaChar.hiraganaToKatakana()
        return directPossibleTypo[normalized]?.map { unit ->
            DirectTypoVariant(katakana = unit.value, weight = unit.weight)
        }.orEmpty()
    }

    private val directPossibleTypo: Map<String, List<TypoUnit>> = mapOf(
        "カ" to listOf(TypoUnit("ガ", weight = 7.0f)),
        "キ" to listOf(TypoUnit("ギ")),
        "ク" to listOf(TypoUnit("グ")),
        "ケ" to listOf(TypoUnit("ゲ")),
        "コ" to listOf(TypoUnit("ゴ")),
        "サ" to listOf(TypoUnit("ザ")),
        "シ" to listOf(TypoUnit("ジ")),
        "ス" to listOf(TypoUnit("ズ")),
        "セ" to listOf(TypoUnit("ゼ")),
        "ソ" to listOf(TypoUnit("ゾ")),
        "タ" to listOf(TypoUnit("ダ", weight = 6.0f)),
        "チ" to listOf(TypoUnit("ヂ")),
        "ツ" to listOf(TypoUnit("ッ", weight = 6.0f), TypoUnit("ヅ", weight = 4.5f)),
        "テ" to listOf(TypoUnit("デ", weight = 6.0f)),
        "ト" to listOf(TypoUnit("ド", weight = 4.5f)),
        "ハ" to listOf(TypoUnit("バ", weight = 4.5f), TypoUnit("パ", weight = 6.0f)),
        "ヒ" to listOf(TypoUnit("ビ"), TypoUnit("ピ", weight = 4.5f)),
        "フ" to listOf(TypoUnit("ブ"), TypoUnit("プ", weight = 4.5f)),
        "ヘ" to listOf(TypoUnit("ベ"), TypoUnit("ペ", weight = 4.5f)),
        "ホ" to listOf(TypoUnit("ボ"), TypoUnit("ポ", weight = 4.5f)),
        "バ" to listOf(TypoUnit("パ")),
        "ビ" to listOf(TypoUnit("ピ")),
        "ブ" to listOf(TypoUnit("プ")),
        "ベ" to listOf(TypoUnit("ペ")),
        "ボ" to listOf(TypoUnit("ポ")),
        "ヤ" to listOf(TypoUnit("ャ")),
        "ユ" to listOf(TypoUnit("ュ")),
        "ヨ" to listOf(TypoUnit("ョ")),
    )

    private val roman2KanaPossibleTypo: Map<String, List<String>> = mapOf(
        "bs" to listOf("ba"),
        "no" to listOf("bo"),
        "li" to listOf("ki"),
        "lo" to listOf("ko"),
        "lu" to listOf("ku"),
        "my" to listOf("mu"),
        "tp" to listOf("to"),
        "ts" to listOf("ta"),
        "wi" to listOf("wo"),
        "pu" to listOf("ou"),
    )

    /** ComposingText の input 列から classic typo 用の読みキーを抽出する。 */
    fun inputTypoKey(elements: List<InputElement>): String {
        return buildString {
            for (element in elements) {
                when (val piece = element.piece) {
                    is InputPiece.Character -> append(piece.value.toString().hiraganaToKatakana())
                    InputPiece.CompositionSeparator -> Unit
                }
            }
        }
    }

    fun classicTypoSearcher(): AzooKeyLoudsTypoSearcher = AzooKeyLoudsTypoSearcher { prefix ->
        if (prefix.isEmpty()) return@AzooKeyLoudsTypoSearcher emptyList()
        if (prefix.any { it.isDigit() || it in '０'..'９' }) return@AzooKeyLoudsTypoSearcher emptyList()
        val normalized = prefix.hiraganaToKatakana()
        val results = mutableListOf<Pair<String, Int>>()

        directPossibleTypo[normalized]?.forEach { unit ->
            results += unit.value to unit.weight.toInt()
        }
        if (normalized.length == 1) {
            results += normalized to 0
        }

        roman2KanaPossibleTypo[normalized.lowercase()]?.forEach { alt ->
            results += alt to 3
        }

        results.distinctBy { it.first }
    }

    fun isDirectOnlyInput(elements: List<InputElement>): Boolean =
        elements.isNotEmpty() && elements.all { it.inputStyle == InputStyle.Direct }

    fun isRoman2KanaCompatibleInput(elements: List<InputElement>): Boolean =
        elements.isNotEmpty() && elements.all {
            it.inputStyle == InputStyle.Roman2Kana || it.inputStyle == InputStyle.Direct
        }
}
