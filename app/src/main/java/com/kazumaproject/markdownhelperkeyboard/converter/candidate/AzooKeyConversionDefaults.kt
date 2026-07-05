package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * AzooKey 本家 [InputManager.getConvertRequestOptions] の固定値・デフォルトに合わせる。
 */
object AzooKeyConversionDefaults {
    /** AzooKey: N_best = 10（設定 UI なし） */
    const val N_BEST = 10

    /** AzooKey v3: left context 最大 20 文字 */
    const val ZENZ_LEFT_CONTEXT_MAX = 40

    /** AzooKey: liveConversion デフォルト ON */
    const val LIVE_CONVERSION_ENABLED = true

    /** AzooKey: automaticCompletionStrength デフォルト weak */
    const val AUTOMATIC_COMPLETION_STRENGTH = "weak"
}
