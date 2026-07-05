package com.kazumaproject.markdownhelperkeyboard.converter.core

import java.lang.Character.UnicodeBlock

/**
 * AzooKey 変換候補向けの日本語テキスト判定。
 * Zenz rich alternative などモデル由来の非日本語トークン（ハングル等）を除外する。
 */
internal object AzooKeyJapaneseConversionText {
    private val allowedAsciiSymbols = setOf(
        ' ', '-', '_', '.', ',', '!', '?', ':', ';', '/', '\\',
        '(', ')', '[', ']', '{', '}', '@', '#', '$', '%', '&', '*', '+', '=',
        '\'', '"', '|', '~', '^', '¥', '€', '£', '·', '￥', '「', '」', '『', '』',
        '（', '）', '［', '］', '｛', '｝', '【', '】', '《', '》', '〈', '〉',
        '、', '。', '・', 'ー', '〜',
    )

    /** Zenz prefix / rich alternative 向け（厳しめ） */
    fun isValidPrefix(prefix: String): Boolean {
        if (prefix.isEmpty() || containsHangul(prefix)) return false
        return prefix.codePoints().allMatch { isAllowedZenzCodePoint(it) }
    }

    /** 辞書・文節候補 surface 向け（絵文字・半角カナ・装飾英数字を許可） */
    fun isValidCandidateSurface(surface: String): Boolean {
        if (surface.isEmpty() || containsHangul(surface)) return false
        return surface.codePoints().allMatch { isAllowedClauseCodePoint(it) }
    }

    /** 候補バー表示から除外すべき surface（Zenz 由来のハングル等の謎候補） */
    fun shouldRejectDisplayedCandidate(surface: String): Boolean {
        if (surface.isEmpty()) return true
        if (containsHangul(surface)) return true
        return false
    }

    fun filterDisplayedCandidates(candidates: List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate>): List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate> {
        return candidates.filterNot { shouldRejectDisplayedCandidate(it.string) }
    }

    fun containsHalfWidthKatakana(text: String): Boolean {
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            if (codePoint in 0xFF66..0xFF9F) return true
            offset += Character.charCount(codePoint)
        }
        return false
    }

    fun isPureHalfWidthKatakana(text: String): Boolean {
        if (text.isEmpty()) return false
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            if (codePoint !in 0xFF66..0xFF9F && codePoint != 0xFF9E && codePoint != 0xFF9F) {
                return false
            }
            offset += Character.charCount(codePoint)
        }
        return true
    }

    fun containsHangul(text: String): Boolean {
        var offset = 0
        while (offset < text.length) {
            if (isHangulCodePoint(text.codePointAt(offset))) return true
            offset += Character.charCount(text.codePointAt(offset))
        }
        return false
    }

    private fun isAllowedZenzCodePoint(codePoint: Int): Boolean {
        if (isHangulCodePoint(codePoint)) return false
        if (codePoint in 0x3041..0x3096) return true // ひらがな
        if (codePoint in 0x30A1..0x30F6) return true // カタカナ
        if (codePoint in 0xFF66..0xFF9F) return true // 半角カナ
        if (codePoint in 'a'.code..'z'.code || codePoint in 'A'.code..'Z'.code) return true
        if (codePoint in '0'.code..'9'.code) return true
        if (codePoint.toChar() in allowedAsciiSymbols) return true
        if (codePoint == 0xEE08) return true // Zenz alignment separator
        if (isCjkIdeographCodePoint(codePoint)) return true
        return false
    }

    private fun isAllowedClauseCodePoint(codePoint: Int): Boolean {
        if (isHangulCodePoint(codePoint)) return false
        if (codePoint == 0x200D) return true // ZWJ (compound emoji)
        if (codePoint in 0x1F3FB..0x1F3FF) return true // emoji skin tone modifiers
        if (isEmojiCodePoint(codePoint)) return true
        if (isDecoratedAlphanumericCodePoint(codePoint)) return true
        if (codePoint in 0x3041..0x3096) return true // ひらがな
        if (codePoint in 0x30A1..0x30F6) return true // カタカナ
        if (codePoint in 0xFF66..0xFF9F) return true // 半角カナ
        if (codePoint in 'a'.code..'z'.code || codePoint in 'A'.code..'Z'.code) return true
        if (codePoint in 0xFF41..0xFF5A || codePoint in 0xFF21..0xFF3A) return true // 全角英字
        if (codePoint in '0'.code..'9'.code || codePoint in 0xFF10..0xFF19) return true // 数字
        if (codePoint.toChar() in allowedAsciiSymbols) return true
        if (codePoint == 0xEE08) return true
        if (isCjkIdeographCodePoint(codePoint)) return true
        return false
    }

    private fun isHangulCodePoint(codePoint: Int): Boolean {
        return when {
            codePoint in 0xAC00..0xD7A3 -> true // Hangul Syllables
            codePoint in 0x1100..0x11FF -> true // Hangul Jamo
            codePoint in 0x3130..0x318F -> true // Hangul Compatibility Jamo
            codePoint in 0xA960..0xA97F -> true // Hangul Jamo Extended-A
            codePoint in 0xD7B0..0xD7FF -> true // Hangul Jamo Extended-B
            else -> false
        }
    }

    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return when {
            codePoint in 0x1F300..0x1FAFF -> true
            codePoint in 0x2600..0x26FF -> true
            codePoint in 0x2700..0x27BF -> true
            codePoint in 0xFE00..0xFE0F -> true // variation selectors
            codePoint in 0x1F1E6..0x1F1FF -> true // flags
            else -> {
                val block = UnicodeBlock.of(codePoint)
                block == UnicodeBlock.MISCELLANEOUS_SYMBOLS ||
                    block == UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS ||
                    block == UnicodeBlock.EMOTICONS ||
                    block == UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS ||
                    block == UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS ||
                    block == UnicodeBlock.DINGBATS
            }
        }
    }

    /** TypographySpecialCandidateProvider 等の装飾英数字 */
    private fun isDecoratedAlphanumericCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x1D400..0x1D7FF || // Mathematical Alphanumeric Symbols
            codePoint in 0x2100..0x214F || // Letterlike Symbols
            codePoint in 0x2460..0x24FF || // Enclosed Alphanumerics
            codePoint in 0x1F100..0x1F1FF // Enclosed Alphanumeric Supplement
    }

    private fun isCjkIdeographCodePoint(codePoint: Int): Boolean {
        val block = UnicodeBlock.of(codePoint)
        return when (block) {
            UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C,
            UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D,
            UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT,
            -> true
            else -> false
        }
    }
}
