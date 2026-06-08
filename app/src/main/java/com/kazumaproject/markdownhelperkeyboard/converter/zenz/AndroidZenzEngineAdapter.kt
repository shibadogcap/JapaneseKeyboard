package com.kazumaproject.markdownhelperkeyboard.converter.zenz

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