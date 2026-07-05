package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.toHiragana

/**
 * AzooKey [TextReplacer](https://github.com/azooKey/AzooKeyKanaKanjiConverter/blob/main/Sources/KanaKanjiConverterModule/ConverterAPI/Replacer/TextReplacer.swift) 相当。
 *
 * 絵文字の完全一致検索とバリエーション置換を提供する。入力中の prefix 検索は [AzooKeyEmojiDictionarySearch.searchDicdataInputPrefix] が担当する。
 */
class AzooKeyTextReplacer private constructor(
    private val emojiSearchDict: Map<String, List<String>>,
    private val emojiGroups: List<EmojiGroup>,
    private val nonBaseEmojis: Set<String>,
) {
    val isEmpty: Boolean
        get() = emojiSearchDict.isEmpty() && emojiGroups.isEmpty() && nonBaseEmojis.isEmpty()

    fun getSearchResult(
        query: String,
        ignoreNonBaseEmoji: Boolean = false,
    ): List<SearchResultItem> {
        val normalizedQuery = query.normalizedEmojiQuery()
        if (normalizedQuery.isEmpty()) return emptyList()
        val candidates = emojiSearchDict[normalizedQuery].orEmpty()
        val filtered = if (ignoreNonBaseEmoji) {
            candidates.filterNot { it in nonBaseEmojis }
        } else {
            candidates
        }
        return filtered.map { SearchResultItem(query = normalizedQuery, text = it) }
    }

    fun getReplacementCandidate(
        left: String,
        center: String,
        @Suppress("UNUSED_PARAMETER") right: String,
    ): List<ReplacementCandidate> {
        val results = mutableListOf<ReplacementCandidate>()
        if (center.codePointCount(0, center.length) == 1) {
            val group = emojiGroups.firstOrNull { center in it.all } ?: return emptyList()
            for (emoji in group.all) {
                if (emoji != center) {
                    results += ReplacementCandidate(
                        target = center,
                        replace = emoji,
                        base = group.base,
                    )
                }
            }
            return results
        }
        if (left.isEmpty()) return emptyList()
        val lastStart = left.offsetByCodePoints(left.length, -1)
        val last = left.substring(lastStart)
        val group = emojiGroups.firstOrNull { last in it.all } ?: return emptyList()
        for (emoji in group.all) {
            if (emoji != last) {
                results += ReplacementCandidate(
                    target = last,
                    replace = emoji,
                    base = group.base,
                )
            }
        }
        return results
    }

    data class SearchResultItem(
        val query: String,
        val text: String,
    )

    data class ReplacementCandidate(
        val target: String,
        val replace: String,
        val base: String,
    ) {
        val text: String get() = replace
    }

    private data class EmojiGroup(
        val base: String,
        val variations: List<String>,
    ) {
        val all: List<String> = listOf(base) + variations
    }

    private fun String.normalizedEmojiQuery(): String {
        return trim().lowercase().toHiragana()
    }

    companion object {
        val empty: AzooKeyTextReplacer = AzooKeyTextReplacer(
            emojiSearchDict = emptyMap(),
            emojiGroups = emptyList(),
            nonBaseEmojis = emptySet(),
        )

        fun fromEmojiTextReplacerText(text: String): AzooKeyTextReplacer {
            val emojiSearchDict = mutableMapOf<String, MutableList<String>>()
            val emojiGroups = mutableListOf<EmojiGroup>()
            val nonBaseEmojis = mutableSetOf<String>()

            for (line in text.lineSequence()) {
                if (line.isBlank()) continue
                val columns = line.split('\t', limit = 3)
                if (columns.size != 3) {
                    return AzooKeyTextReplacer(emojiSearchDict, emojiGroups, nonBaseEmojis)
                }
                val base = columns[0].trim()
                if (base.isEmpty()) continue
                val variations = columns[2]
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                for (query in columns[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
                    val normalized = query.lowercase().toHiragana()
                    val bucket = emojiSearchDict.getOrPut(normalized) { mutableListOf() }
                    bucket += base
                    bucket += variations
                }
                nonBaseEmojis += variations
                emojiGroups += EmojiGroup(base = base, variations = variations)
            }

            return AzooKeyTextReplacer(
                emojiSearchDict = emojiSearchDict.mapValues { (_, values) -> values.distinct() },
                emojiGroups = emojiGroups,
                nonBaseEmojis = nonBaseEmojis,
            )
        }
    }
}
