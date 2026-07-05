package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPromptContext

class FakeZenzEnginePort(
    private val generateResult: String = "",
    private val evaluateResult: String = "",
) : ZenzEnginePort {
    override suspend fun generateWithContext(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        maxTokens: Int,
    ): String = generateResult

    override suspend fun predictNextInputText(
        prompt: ZenzPromptContext,
        composingText: String,
        count: Int,
        minLength: Int,
        maxEntropy: Float?,
        possibleNexts: List<String>,
    ): String = generateResult.take(count)

    override suspend fun typoEncodeRaw(text: String): IntArray = IntArray(0)

    override suspend fun typoNextLogProbs(
        promptPrefix: String,
        emittedTokenIds: IntArray,
    ): FloatArray? = null

    override fun typoTokenToSingleCharacter(tokenId: Int): Char? = null

    override fun vocabSize(): Int = 0

    override suspend fun scoreCandidates(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        candidates: List<String>,
    ): FloatArray = FloatArray(candidates.size) { 0.5f }

    override suspend fun candidateEvaluate(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        candidate: String,
        requestRichCandidates: Boolean,
    ): String = evaluateResult
}
