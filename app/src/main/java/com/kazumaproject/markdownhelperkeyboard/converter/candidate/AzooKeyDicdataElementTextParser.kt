package com.kazumaproject.markdownhelperkeyboard.converter.candidate

object AzooKeyDicdataElementTextParser {
    fun parseLines(
        text: String,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
    ): List<AzooKeyDictionaryEntry> {
        return text
            .lineSequence()
            .mapNotNull { parseLine(it, sourceKind) }
            .toList()
    }

    fun parseLine(
        line: String,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
    ): AzooKeyDictionaryEntry? {
        if (line.isBlank()) {
            return null
        }
        val columns = line.split('\t')
        if (columns.size < 6) {
            return null
        }

        val ruby = columns[0]
        if (ruby.isBlank()) {
            return null
        }

        val word = columns[1].ifBlank { ruby }
        val leftId = columns[2].toIntOrNull() ?: 0
        val rightId = columns[3].toIntOrNull() ?: leftId
        val mid = columns[4].toIntOrNull() ?: 0
        val baseValue = columns[5].toFloatOrNull() ?: -30f
        val adjust = columns.getOrNull(6)?.toFloatOrNull() ?: 0f
        val value = AzooKeyPValues.clampDictionaryValue(baseValue + adjust)

        return AzooKeyDictionaryEntry(
            surface = word,
            reading = ruby,
            leftId = leftId,
            rightId = rightId,
            mid = mid,
            wordCost = value.toInt(),
            value = value,
            sourceKind = sourceKind,
        )
    }
}
