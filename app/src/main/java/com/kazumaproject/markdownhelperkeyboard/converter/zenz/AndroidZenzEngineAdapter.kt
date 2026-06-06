package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.zenz.ZenzEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidZenzEngineAdapter @Inject constructor() : ZenzEnginePort {
    override suspend fun generateWithContext(
        profile: String,
        leftContext: String,
        inputKatakana: String,
        maxTokens: Int,
    ): String {
        return ZenzEngine.generateWithContextAndConditions(
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
    ): String {
        return ZenzEngine.candidateEvaluate(
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
    ): FloatArray {
        if (candidates.isEmpty()) return FloatArray(0)
        return ZenzEngine.scoreCandidates(
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