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
        '\'', '"', '|', '~', '^', '¥', '€', '£', '·', '￥', '「', '」', 'ー', '〜',
    )

    fun isValidPrefix(prefix: String): Boolean {
        if (prefix.isEmpty()) return false
        return prefix.all { isAllowedConversionChar(it) }
    }

    fun isValidCandidateSurface(surface: String): Boolean {
        if (surface.isEmpty()) return false
        return surface.all { isAllowedConversionChar(it) }
    }

    private fun isAllowedConversionChar(char: Char): Boolean {
        if (char in '\u3041'..'\u3096') return true // ひらがな
        if (char in '\u30A1'..'\u30F6') return true // カタカナ
        if (char in '\uFF66'..'\uFF9F') return true // 半角カナ
        if (char in 'a'..'z' || char in 'A'..'Z') return true
        if (char in 'ａ'..'ｚ' || char in 'Ａ'..'Ｚ') return true
        if (char in '0'..'9' || char in '０'..'９') return true
        if (char in allowedAsciiSymbols) return true
        if (char == '\uEE08') return true // Zenz alignment separator
        if (isCjkIdeograph(char)) return true
        return false
    }

    private fun isCjkIdeograph(char: Char): Boolean {
        return when (UnicodeBlock.of(char)) {
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
