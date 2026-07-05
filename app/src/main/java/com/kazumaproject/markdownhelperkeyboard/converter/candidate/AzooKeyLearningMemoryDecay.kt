package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.util.concurrent.TimeUnit

/**
 * AzooKey [LearningMemory](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の decay / 上限。
 */
object AzooKeyLearningMemoryDecay {
    const val HALF_LIFE_DAYS: Int = 32
    const val DELETE_AFTER_DAYS: Int = 128
    const val MAX_MEMORY_COUNT: Int = 65536
    const val PAUSE_MARKER_FILE: String = ".pause"

    fun todayEpochDay(): Int {
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()).toInt()
    }

    /**
     * 128 日未使用エントリを除外し、32 日ごとに legacy score を半減する。
     * Room に日付列が無い場合は [lastUsedDay]=today として upsert 時のみ更新される。
     */
    fun applyDecayToScore(
        score: Int,
        lastUpdatedDay: Int,
        lastUsedDay: Int,
        today: Int = todayEpochDay(),
    ): Int? {
        if (today - lastUsedDay >= DELETE_AFTER_DAYS) {
            return null
        }
        var updatedDay = lastUpdatedDay
        var decayed = score.coerceAtLeast(1)
        while (today - updatedDay > HALF_LIFE_DAYS) {
            updatedDay += HALF_LIFE_DAYS
            decayed = (decayed / 2).coerceAtLeast(1)
        }
        return decayed
    }

    /** 65536 超過時は reading 長の降順で drop（本家 longest drop 相当の近似）。 */
    fun trimToMaxCount(entries: List<AzooKeyDictionaryEntry>): List<AzooKeyDictionaryEntry> {
        if (entries.size <= MAX_MEMORY_COUNT) return entries
        return entries
            .sortedByDescending { it.reading.length }
            .take(MAX_MEMORY_COUNT)
    }
}
