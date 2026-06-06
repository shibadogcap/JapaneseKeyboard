package com.kazumaproject.markdownhelperkeyboard.converter.zenz

/**
 * Android [com.kazumaproject.zenz.ZenzEngine] への境界。converter domain は framework 非依存。
 */
interface ZenzEnginePort {
    suspend fun generateWithContext(
        profile: String,
        leftContext: String,
        inputKatakana: String,
        maxTokens: Int,
    ): String

    suspend fun candidateEvaluate(
        profile: String,
        leftContext: String,
        inputKatakana: String,
        candidate: String,
    ): String

    suspend fun scoreCandidates(
        profile: String,
        leftContext: String,
        inputKatakana: String,
        candidates: List<String>,
    ): FloatArray
}