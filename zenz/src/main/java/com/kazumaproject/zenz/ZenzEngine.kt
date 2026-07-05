package com.kazumaproject.zenz

object ZenzEngine {

    init {
        // CMake の add_library(zenz SHARED ...) と一致させる
        System.loadLibrary("zenz")
    }

    external fun initModel(modelPath: String)
    external fun setRuntimeConfig(
        nCtx: Int,
        nThreads: Int
    )

    external fun generate(
        prompt: String,
        maxTokens: Int
    ): String

    external fun generateWithContext(
        leftContext: String,
        input: String,
        maxTokens: Int
    ): String

    external fun generateWithContextAndConditions(
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        rightContext: String,
        input: String,
        maxTokens: Int
    ): String

    external fun candidateEvaluate(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String,
        candidate: String,
        requestRichCandidates: Boolean,
    ): String

    external fun scoreCandidates(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String,
        candidates: Array<String>
    ): FloatArray

    /** AzooKey [ZenzInputTextGenerator](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
    external fun predictNextInputText(
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        rightContext: String,
        input: String,
        count: Int,
        minLength: Int,
        maxEntropy: Float,
        possibleNexts: Array<String>,
    ): String

    external fun vocabSize(): Int

    external fun typoEncodeRaw(text: String): IntArray

    external fun typoTokenToSingleCharacter(tokenId: Int): String

    external fun typoNextLogProbs(promptPrefix: String, emittedTokenIds: IntArray): FloatArray
}
