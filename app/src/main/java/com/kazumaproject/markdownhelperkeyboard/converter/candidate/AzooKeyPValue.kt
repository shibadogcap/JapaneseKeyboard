package com.kazumaproject.markdownhelperkeyboard.converter.candidate

typealias AzooKeyPValue = Float

object AzooKeyPValues {
    fun clampDictionaryValue(value: Float): AzooKeyPValue {
        return minOf(0f, value)
    }

    fun learningMemoryValue(
        rubyLength: Int,
        count: Int,
    ): AzooKeyPValue {
        val safeLength = rubyLength.coerceAtLeast(1)
        val safeCount = count.coerceIn(1, 255)
        val d = 1.0 - safeCount.toDouble() / 255.0
        return (-1.0 - 4.0 / safeLength.toDouble() - 3.0 * d * d * d).toFloat()
    }

    fun legacyLearnScoreToLearningMemoryValue(
        rubyLength: Int,
        legacyScore: Int,
    ): AzooKeyPValue {
        val clampedScore = legacyScore.coerceIn(0, LEGACY_MAX_LEARN_SCORE)
        val estimatedCount = 1 + (LEGACY_MAX_LEARN_SCORE - clampedScore) / LEGACY_SCORE_STEP
        return learningMemoryValue(
            rubyLength = rubyLength,
            count = estimatedCount,
        )
    }

    private const val LEGACY_MAX_LEARN_SCORE = 5500
    private const val LEGACY_SCORE_STEP = 1200
}
