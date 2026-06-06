package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.toHiragana

object AzooKeyEmojiDictionaryTextParser {
    fun parse(text: String): List<AzooKeyDictionaryEntry> {
        return text
            .lineSequence()
            .filter { it.isNotBlank() }
            .flatMap { line -> parseLine(line).asSequence() }
            .distinctBy { "${it.reading}\u0000${it.surface}" }
            .toList()
    }

    private fun parseLine(line: String): List<AzooKeyDictionaryEntry> {
        val columns = line.split('\t', limit = 3)
        if (columns.size != 3) {
            return emptyList()
        }

        val base = columns[0].trim()
        if (base.isBlank()) {
            return emptyList()
        }

        val variationEmojis = columns[2]
            .splitToSequence(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it to true }
            .toList()
        val emojis = listOf(base to false) + variationEmojis

        val queries = columns[1]
            .splitToSequence(',')
            .map { it.trim().lowercase().toHiragana() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        return queries.flatMap { query ->
            emojis.map { (emoji, isVariation) ->
                AzooKeyDictionaryEntryMapper.emoji(
                    surface = emoji,
                    reading = query,
                    score = -3,
                    metadata = if (isVariation) {
                        setOf(
                            AzooKeyDictionaryMetadata.EmojiTextReplacer,
                            AzooKeyDictionaryMetadata.EmojiVariation,
                        )
                    } else {
                        setOf(AzooKeyDictionaryMetadata.EmojiTextReplacer)
                    },
                )
            }
        }
    }
}
