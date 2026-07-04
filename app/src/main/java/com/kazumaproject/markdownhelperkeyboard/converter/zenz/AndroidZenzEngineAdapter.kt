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
        profile: String,
        leftContext: String,
        inputKatakana: String,
        maxTokens: Int,
    ): String = withContext(Dispatchers.Default) {
        ZenzEngine.generateWithContextAndConditions(
            profile = profile,
            topic = "",
            style = "",
            preference = "",
            leftContext = leftContext,
            input = inputKatakana,
            maxTokens = maxTokens,
        ) ?: ""
    }

    override suspend fun predictNextInputText(
        profile: String,
        leftSideContext: String,
        composingText: String,
        count: Int,
        possibleNexts: List<String>,
    ): String = withContext(Dispatchers.Default) {
        if (count <= 0 || composingText.isEmpty()) return@withContext ""
        val generated = ZenzEngine.generateWithContextAndConditions(
            profile = profile,
            topic = "",
            style = "",
            preference = "",
            leftContext = leftSideContext,
            input = composingText.hiraganaToKatakana(),
            maxTokens = count,
        )?.trim().orEmpty()
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
        profile: String,
        leftContext: String,
        inputKatakana: String,
        candidate: String,
    ): String = withContext(Dispatchers.Default) {
        ZenzEngine.candidateEvaluate(
            profile = profile,
            topic = "",
            style = "",
            preference = "",
            leftContext = leftContext,
            input = inputKatakana,
            candidate = candidate,
        ) ?: ""
    }

    override suspend fun scoreCandidates(
        profile: String,
        leftContext: String,
        inputKatakana: String,
        candidates: List<String>,
    ): FloatArray = withContext(Dispatchers.Default) {
        if (candidates.isEmpty()) return@withContext FloatArray(0)
        ZenzEngine.scoreCandidates(
            profile = profile,
            topic = "",
            style = "",
            preference = "",
            leftContext = leftContext,
            input = inputKatakana,
            candidates = candidates.toTypedArray(),
        )
    }
}