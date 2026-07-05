package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.extensions.katakanaToHiragana
import com.kazumaproject.zenz.ZenzEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidZenzEngineAdapter @Inject constructor() : ZenzEnginePort {
    override suspend fun generateWithContext(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        maxTokens: Int,
    ): String = withContext(Dispatchers.Default) {
        ZenzEngine.generateWithContextAndConditions(
            profile = prompt.profile,
            topic = prompt.topic,
            style = prompt.style,
            preference = prompt.preference,
            leftContext = prompt.leftContext,
            rightContext = prompt.rightContext,
            input = inputKatakana,
            maxTokens = maxTokens,
        )
    }

    override suspend fun predictNextInputText(
        prompt: ZenzPromptContext,
        composingText: String,
        count: Int,
        minLength: Int,
        maxEntropy: Float?,
        possibleNexts: List<String>,
    ): String = withContext(Dispatchers.Default) {
        if (count <= 0 || composingText.isEmpty()) return@withContext ""
        val normalizedNexts = possibleNexts
            .filter { it.isNotEmpty() }
            .map { it.hiraganaToKatakana() }
            .toTypedArray()
        ZenzEngine.predictNextInputText(
            profile = prompt.profile,
            topic = prompt.topic,
            style = prompt.style,
            preference = prompt.preference,
            leftContext = prompt.leftContext,
            rightContext = prompt.rightContext,
            input = composingText.hiraganaToKatakana(),
            count = count,
            minLength = minLength.coerceAtLeast(1).coerceAtMost(count),
            maxEntropy = maxEntropy ?: -1f,
            possibleNexts = normalizedNexts,
        )
    }

    override suspend fun typoEncodeRaw(text: String): IntArray = withContext(Dispatchers.Default) {
        ZenzEngine.typoEncodeRaw(text)
    }

    override suspend fun typoNextLogProbs(
        promptPrefix: String,
        emittedTokenIds: IntArray,
    ): FloatArray? = withContext(Dispatchers.Default) {
        val values = ZenzEngine.typoNextLogProbs(promptPrefix, emittedTokenIds)
        if (values.isEmpty()) null else values
    }

    override fun typoTokenToSingleCharacter(tokenId: Int): Char? {
        val text = ZenzEngine.typoTokenToSingleCharacter(tokenId)
        return text.firstOrNull()
    }

    override fun vocabSize(): Int = ZenzEngine.vocabSize()

    override suspend fun candidateEvaluate(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        candidate: String,
        requestRichCandidates: Boolean,
    ): String = withContext(Dispatchers.Default) {
        ZenzEngine.candidateEvaluate(
            profile = prompt.profile,
            topic = prompt.topic,
            style = prompt.style,
            preference = prompt.preference,
            leftContext = prompt.leftContext,
            rightContext = prompt.rightContext,
            input = inputKatakana,
            candidate = candidate,
            requestRichCandidates = requestRichCandidates,
        )
    }

    override suspend fun scoreCandidates(
        prompt: ZenzPromptContext,
        inputKatakana: String,
        candidates: List<String>,
    ): FloatArray = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext FloatArray(0)
        ZenzEngine.scoreCandidates(
            profile = prompt.profile,
            topic = prompt.topic,
            style = prompt.style,
            preference = prompt.preference,
            leftContext = prompt.leftContext,
            rightContext = prompt.rightContext,
            input = inputKatakana,
            candidates = candidates.toTypedArray(),
        )
    }
}
