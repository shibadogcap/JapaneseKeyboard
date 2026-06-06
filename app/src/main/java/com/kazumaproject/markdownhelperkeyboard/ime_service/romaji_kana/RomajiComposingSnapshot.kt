package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

/**
 * [RomajiKanaConverter] の確定かな + 未確定ローマ字バッファ。
 * AzooKey [ComposingText] の Roman2Kana セグメント構築に使う。
 */
data class RomajiComposingSnapshot(
    val committedSurface: String,
    val pendingRomaji: String,
) {
    val displayText: String
        get() = committedSurface + pendingRomaji
}