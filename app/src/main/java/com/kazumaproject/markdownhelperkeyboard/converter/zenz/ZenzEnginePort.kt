package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions

/**
 * Android [com.kazumaproject.zenz.ZenzEngine] への境界。converter domain は framework 非依存。
 */
interface ZenzEnginePort {
    suspend fun generateWithContext(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        maxTokens: Int,
    ): String

    /** AzooKey `Zenz.predictNextInputText` 相当（v3 input prediction）。 */
    suspend fun predictNextInputText(
        prompt: ZenzPromptContext,
        composingText: String,
        count: Int,
        minLength: Int = 1,
        maxEntropy: Float? = null,
        possibleNexts: List<String> = emptyList(),
    ): String

    /** typo correction LM（ZenzCompatibleInputLanguageModelContext 相当）。 */
    suspend fun typoEncodeRaw(text: String): IntArray

    suspend fun typoNextLogProbs(promptPrefix: String, emittedTokenIds: IntArray): FloatArray?

    fun typoTokenToSingleCharacter(tokenId: Int): Char?

    fun vocabSize(): Int

    suspend fun candidateEvaluate(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        candidate: String,
        requestRichCandidates: Boolean = false,
    ): String

    suspend fun scoreCandidates(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        candidates: List<String>,
    ): FloatArray
}

fun ConvertRequestOptions.toZenzPromptContext(leftContext: String): ZenzPromptContext {
    return ZenzPromptContext(
        profile = zenzProfile,
        topic = zenzTopic,
        style = zenzStyle,
        preference = zenzPreference,
        leftContext = leftContext,
        rightContext = zenzRightSideContext,
    )
}

fun ZenzConversionConfig.toZenzPromptContext(leftContext: String, rightContext: String = ""): ZenzPromptContext {
    return ZenzPromptContext(
        profile = profile,
        topic = topic,
        style = style,
        preference = preference,
        leftContext = leftContext,
        rightContext = rightContext,
    )
}
