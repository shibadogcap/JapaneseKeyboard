package com.kazumaproject.markdownhelperkeyboard.converter.zenz

/**
 * AzooKey [zenzaiEffort](https://github.com/azooKey/azooKey) 相当の inferenceLimit マッピング。
 */
enum class AzooKeyZenzaiEffort {
    High,
    Medium,
    Low;

    val inferenceLimit: Int
        get() = when (this) {
            High -> 3
            Medium -> 1
            Low -> 2
        }
}

/** Swift [EfficientNGram](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 設定（JNI 未接続時は no-op）。 */
data class AzooKeyEfficientNGramConfig(
    val prefix: String,
    val n: Int = 5,
    val d: Double = 0.75,
)

/** Swift [ZenzaiTypoCandidateGenerator](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 設定プレースホルダ。 */
data class AzooKeyZenzaiTypoConfig(
    val enabled: Boolean = false,
    val languageModel: AzooKeyEfficientNGramConfig? = null,
)
