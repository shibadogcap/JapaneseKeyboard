package com.kazumaproject.zenz

object ZenzEngine {
    fun initModel(modelPath: String) = Unit

    fun setRuntimeConfig(
        nCtx: Int,
        nThreads: Int
    ) = Unit

    fun generate(
        prompt: String,
        maxTokens: Int
    ): String = ""

    fun generateWithContext(
        leftContext: String,
        input: String,
        maxTokens: Int
    ): String = ""

    fun generateWithContextAndConditions(
        profile: String,
        topic: String,
        style: String,
        preference: String,
        leftContext: String,
        rightContext: String,
        input: String,
        maxTokens: Int
    ): String = ""

    fun candidateEvaluate(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String,
        candidate: String,
        requestRichCandidates: Boolean,
    ): String = ""

    fun scoreCandidates(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String,
        candidates: Array<String>
    ): FloatArray = FloatArray(candidates.size)

    fun predictNextInputText(
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
    ): String = ""

    fun vocabSize(): Int = 0

    fun typoEncodeRaw(text: String): IntArray = IntArray(0)

    fun typoTokenToSingleCharacter(tokenId: Int): String = ""

    fun typoNextLogProbs(promptPrefix: String, emittedTokenIds: IntArray): FloatArray = FloatArray(0)
}
