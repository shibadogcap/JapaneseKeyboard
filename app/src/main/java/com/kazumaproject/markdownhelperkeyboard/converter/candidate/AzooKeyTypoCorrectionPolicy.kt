package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * AzooKey [KanaKanjiConverter.isClassicTypoCorrectionEnabled](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 * Android は iOS と同様 `.automatic` で classic typo を有効にする。
 */
object AzooKeyTypoCorrectionPolicy {
    fun isClassicTypoCorrectionEnabled(mode: AzooKeyStyleTypoCorrectionMode): Boolean =
        when (mode) {
            AzooKeyStyleTypoCorrectionMode.Enabled,
            AzooKeyStyleTypoCorrectionMode.Automatic,
            -> true
            AzooKeyStyleTypoCorrectionMode.Disabled -> false
        }
}
