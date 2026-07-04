package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana

/**
 * AzooKey Roman2Kana 変換。
 * デフォルトは [AzooKeyInputTable.Default]（Swift `defaultRoman2Kana` 互換）。
 * カスタムローマ字表は [fromMap] でユーザー編集テーブルを維持。
 */
class AzooKeyRoman2KanaTransducer private constructor(
    private val inputTable: AzooKeyInputTable,
) {
    fun possibleNexts(romanPrefix: String): List<String> =
        inputTable.possibleNexts(romanPrefix)

    /** 逐次 apply（ComposingText 用）。 */
    fun apply(buffer: MutableList<Char>, added: Char): Int =
        inputTable.apply(buffer, added)

    /** 完成形ローマ字列の convertTarget。 */
    fun convert(romaji: String): String = inputTable.convert(romaji)

    val table: AzooKeyInputTable get() = inputTable

    companion object {
        val Identity: AzooKeyRoman2KanaTransducer =
            AzooKeyRoman2KanaTransducer(AzooKeyInputTable.Empty)

        /** AzooKey 本家 defaultRoman2Kana（非カスタム時）。 */
        fun default(): AzooKeyRoman2KanaTransducer =
            AzooKeyRoman2KanaTransducer(AzooKeyInputTable.Default)

        /** ユーザー編集ローマ字表。 */
        fun fromMap(map: Map<String, Pair<String, Int>>): AzooKeyRoman2KanaTransducer =
            AzooKeyRoman2KanaTransducer(AzooKeyInputTable.fromRomajiMap(map))

        /** @deprecated テスト互換。map から構築。 */
        fun fromDefaultInputTable(map: Map<String, Pair<String, Int>>): AzooKeyRoman2KanaTransducer =
            fromMap(map)

        /** AzooKey InputTable.possibleNexts 構築（テスト・レガシー互換） */
        internal fun buildPossibleNexts(map: Map<String, Pair<String, Int>>): Map<String, List<String>> {
            val stringMap = map.mapValues { it.value.first }
            if (stringMap.isEmpty()) return emptyMap()
            val results = mutableMapOf<String, MutableList<String>>()
            for ((key, value) in stringMap) {
                val katakana = value.hiraganaToKatakana()
                for (prefixCount in 1 until key.length) {
                    val prefix = key.substring(0, prefixCount).lowercase()
                    results.getOrPut(prefix) { mutableListOf() }.add(katakana)
                }
            }
            return results
        }
    }
}

