package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import kotlin.math.pow

/**
 * AzooKey [LongTermLearningMemory.valueForData](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
object AzooKeyLearningMemoryValue {
    fun pValue(
        readingLength: Int,
        occurrenceCount: Int = 1,
    ): AzooKeyPValue {
        val length = readingLength.coerceAtLeast(1)
        val d = 1.0 - occurrenceCount.coerceIn(1, 255) / 255.0
        return (-1.0 - 4.0 / length - 3.0 * d.pow(3.0)).toFloat()
    }

    fun entryFromLearnEntity(entity: LearnEntity): AzooKeyDictionaryEntry {
        val reading = entity.input
        return AzooKeyDictionaryEntryMapper.memory(
            surface = entity.out,
            reading = reading,
            leftId = entity.leftId?.toInt(),
            rightId = entity.rightId?.toInt(),
            legacyScore = entity.score.toInt(),
            readingLength = reading.length,
        )
    }
}