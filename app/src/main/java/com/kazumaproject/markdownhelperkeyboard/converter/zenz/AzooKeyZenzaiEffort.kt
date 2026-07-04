package com.kazumaproject.markdownhelperkeyboard.converter.zenz

/**
 * AzooKey [zenzaiEffort](https://github.com/azooKey/azooKey) 相当の inferenceLimit マッピング。
 *
 * EfficientNGram / ZenzaiTypoCandidateGenerator は後段フェーズで port 予定。
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
