package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.toHiragana

class AzooKeyEmojiDictionarySearch(
    private val index: AzooKeyDictionaryEntryIndex,
) {
    fun searchInputPrefix(input: String, limit: Int): List<AzooKeyDictionaryEntry> {
        val normalizedInput = input.normalizedEmojiQuery()
        val dicdataEntries = index.searchPrefix(
            prefix = normalizedInput,
            sourceKind = AzooKeyDictionarySourceKind.Emoji,
            limit = limit,
        ).filter { AzooKeyDictionaryMetadata.EmojiDicdata in it.metadata }

        if (dicdataEntries.size >= limit) {
            return dicdataEntries.take(limit)
        }

        val textReplacerEntries = index.searchPrefix(
            prefix = normalizedInput,
            sourceKind = AzooKeyDictionarySourceKind.Emoji,
            limit = limit,
        ).filter { AzooKeyDictionaryMetadata.EmojiDicdata !in it.metadata }

        return (dicdataEntries + textReplacerEntries)
            .distinctBy { it.surface }
            .take(limit)
    }

    /** 入力中変換: emoji dicdata の prefix のみ（AzooKey lattice 相当。TextReplacer は使わない）。 */
    fun searchDicdataInputPrefix(input: String, limit: Int): List<AzooKeyDictionaryEntry> {
        if (input.isBlank() || limit <= 0) return emptyList()
        return index.searchPrefix(
            prefix = input.normalizedEmojiQuery(),
            sourceKind = AzooKeyDictionarySourceKind.Emoji,
            limit = limit,
        ).filter { AzooKeyDictionaryMetadata.EmojiDicdata in it.metadata }
            .distinctBy { it.surface }
            .take(limit)
    }

    fun searchPostCommit(
        committedText: String,
        limit: Int,
        committedReading: String? = null,
    ): List<AzooKeyDictionaryEntry> {
        val merged = linkedMapOf<String, AzooKeyDictionaryEntry>()
        fun collect(query: String) {
            index.searchExact(
                reading = query.normalizedEmojiQuery(),
                sourceKind = AzooKeyDictionarySourceKind.Emoji,
                limit = limit,
            ).forEach { entry -> merged[entry.surface] = entry }
        }
        collect(committedText)
        committedReading
            ?.takeIf { it.isNotBlank() }
            ?.let { reading ->
                if (reading.normalizedEmojiQuery() != committedText.normalizedEmojiQuery()) {
                    collect(reading)
                }
            }
        return merged.values.take(limit).toList()
    }

    private fun String.normalizedEmojiQuery(): String {
        return trim().lowercase().toHiragana()
    }

    companion object {
        fun fromAzooKeyEmojiDictionaryText(text: String): AzooKeyEmojiDictionarySearch {
            return fromEntries(
                entries = AzooKeyEmojiDictionaryTextParser.parse(text),
            )
        }

        fun fromAzooKeyEmojiDictionaryTexts(
            textReplacerText: String,
            dicdataText: String?,
        ): AzooKeyEmojiDictionarySearch {
            return fromEntries(
                entries = buildList {
                    addAll(AzooKeyEmojiDictionaryTextParser.parse(textReplacerText))
                    if (dicdataText != null) {
                        addAll(AzooKeyEmojiDicdataTextParser.parse(dicdataText))
                    }
                }
            )
        }

        fun fromEntries(entries: Iterable<AzooKeyDictionaryEntry>): AzooKeyEmojiDictionarySearch {
            return AzooKeyEmojiDictionarySearch(
                index = AzooKeyDictionaryEntryIndex(
                    entries = entries,
                )
            )
        }
    }
}
