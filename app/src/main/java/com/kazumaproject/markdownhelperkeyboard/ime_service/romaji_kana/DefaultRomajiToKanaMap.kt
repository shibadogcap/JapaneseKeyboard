package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyDefaultRoman2KanaData

/**
 * デフォルトローマ字→かな変換テーブル。
 * 本家 AzooKey [defaultRoman2Kana](https://github.com/azooKey/AzooKeyKanaKanjiConverter) と同一。
 * [RomajiKanaConverter] とカスタムローマ字 UI の非削除デフォルト表に使用。
 */
object DefaultRomajiToKanaMap {
    val data: Map<String, Pair<String, Int>> =
        AzooKeyDefaultRoman2KanaData.stringMap.map { (key, kana) ->
            key to (kana to key.length)
        }.toMap()
}
