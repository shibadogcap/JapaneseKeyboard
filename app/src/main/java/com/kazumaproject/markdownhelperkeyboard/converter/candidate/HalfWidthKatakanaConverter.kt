package com.kazumaproject.markdownhelperkeyboard.converter.candidate

object HalfWidthKatakanaConverter {
    private val FULL_TO_HALF_KATAKANA = mapOf(
        'ア' to "ｱ", 'イ' to "ｲ", 'ウ' to "ｳ", 'エ' to "ｴ", 'オ' to "ｵ",
        'カ' to "ｶ", 'キ' to "ｷ", 'ク' to "ｸ", 'ケ' to "ｹ", 'コ' to "ｺ",
        'サ' to "ｻ", 'シ' to "ｼ", 'ス' to "ｽ", 'セ' to "ｾ", 'ソ' to "ｿ",
        'タ' to "ﾀ", 'チ' to "ﾁ", 'ツ' to "ﾂ", 'テ' to "ﾃ", 'ト' to "ﾄ",
        'ナ' to "ﾅ", 'ニ' to "ﾆ", 'ヌ' to "ﾇ", 'ネ' to "ﾈ", 'ノ' to "ﾉ",
        'ハ' to "ﾊ", 'ヒ' to "ﾋ", 'フ' to "ﾌ", 'ヘ' to "ﾍ", 'ホ' to "ﾎ",
        'マ' to "ﾏ", 'ミ' to "ﾐ", 'ム' to "ﾑ", 'メ' to "ﾒ", 'モ' to "ﾓ",
        'ヤ' to "ﾔ", 'ユ' to "ﾕ", 'ヨ' to "ﾖ",
        'ラ' to "ﾗ", 'リ' to "ﾘ", 'ル' to "ﾙ", 'レ' to "ﾚ", 'ロ' to "ﾛ",
        'ワ' to "ﾜ", 'ヲ' to "ｦ", 'ン' to "ﾝ",
        'ガ' to "ｶﾞ", 'ギ' to "ｷﾞ", 'グ' to "ｸﾞ", 'ゲ' to "ｹﾞ", 'ゴ' to "ｺﾞ",
        'ザ' to "ｻﾞ", 'ジ' to "ｼﾞ", 'ズ' to "ｽﾞ", 'ゼ' to "ｾﾞ", 'ゾ' to "ｿﾞ",
        'ダ' to "ﾀﾞ", 'ヂ' to "ﾁﾞ", 'ヅ' to "ﾂﾞ", 'デ' to "ﾃﾞ", 'ド' to "ﾄﾞ",
        'バ' to "ﾊﾞ", 'ビ' to "ﾋﾞ", 'ブ' to "ﾌﾞ", 'ベ' to "ﾍﾞ", 'ボ' to "ﾎﾞ",
        'パ' to "ﾊﾟ", 'ピ' to "ﾋﾟ", 'プ' to "ﾌﾟ", 'ペ' to "ﾍﾟ", 'ポ' to "ﾎﾟ",
        'ッ' to "ｯ", 'ャ' to "ｬ", 'ュ' to "ｭ", 'ョ' to "ｮ",
        'ァ' to "ｧ", 'ィ' to "ｨ", 'ゥ' to "ｩ", 'ェ' to "ｪ", 'ォ' to "ｫ",
        'ー' to "ｰ",
    )

    fun convertFullWidthKatakana(text: String): String {
        if (text.isEmpty()) return text
        return buildString {
            for (ch in text) {
                append(FULL_TO_HALF_KATAKANA[ch] ?: ch)
            }
        }
    }

    fun isConvertibleFullWidthKatakana(text: String): Boolean {
        return text.isNotEmpty() && text.all { it in FULL_TO_HALF_KATAKANA }
    }
}
