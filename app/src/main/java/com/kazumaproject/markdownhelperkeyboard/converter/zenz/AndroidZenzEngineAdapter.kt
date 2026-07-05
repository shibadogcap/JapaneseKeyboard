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
        possibleNexts: List<String>,
    ): String = withContext(Dispatchers.Default) {
        if (count <= 0 || composingText.isEmpty()) return@withContext ""
        val generated = ZenzEngine.generateWithContextAndConditions(
            profile = prompt.profile,
            topic = prompt.topic,
            style = prompt.style,
            preference = prompt.preference,
            leftContext = prompt.leftContext,
            rightContext = prompt.rightContext,
            input = composingText.hiraganaToKatakana(),
            maxTokens = count,
        ).trim()
        if (generated.isEmpty()) return@withContext ""
        if (possibleNexts.isEmpty()) {
            return@withContext generated.take(count)
        }
        val allowedPrefixes = possibleNexts.filter { it.isNotEmpty() }
        var candidate = ""
        for (ch in generated) {
            val next = candidate + ch
            val normalized = next.hiraganaToKatakana()
            if (allowedPrefixes.none { it.startsWith(normalized) }) break
            candidate = next
            if (candidate.length >= count) break
        }
        candidate
    }

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
