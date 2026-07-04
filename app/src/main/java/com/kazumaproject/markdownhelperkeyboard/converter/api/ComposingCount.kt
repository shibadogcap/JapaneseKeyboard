package com.kazumaproject.markdownhelperkeyboard.converter.api

/**
 * AzooKey [ComposingCount](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
sealed interface ComposingCount {
    data class InputCount(val count: Int) : ComposingCount
    data class SurfaceCount(val count: Int) : ComposingCount
    data class Composite(val left: ComposingCount, val right: ComposingCount) : ComposingCount

    companion object {
        fun fromReadingLength(length: Int): ComposingCount = SurfaceCount(length)
    }
}
