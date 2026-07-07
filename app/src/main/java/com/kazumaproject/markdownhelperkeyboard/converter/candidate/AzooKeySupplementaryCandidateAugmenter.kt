package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine

/**
 * AzooKey 相当の supplementary 候補付与。
 *
 * - 入力中: emoji dicdata の prefix 検索のみ（TextReplacer は確定後専用）
 * - 表示: [CandidateLanePresentation.mergeForDisplay] で候補バーへ合流、ライブ変換は main のみ
 */
object AzooKeySupplementaryCandidateAugmenter {
    fun appendDicdataEmojiInputCandidates(
        input: ComposingText,
        result: AzooKeyStyleConversionResult,
        emojiDictionarySearch: AzooKeyEmojiDictionarySearch?,
        kanaKanjiEngine: KanaKanjiEngine,
        limit: Int,
    ): AzooKeyStyleConversionResult {
        val convertTarget = input.convertTarget
        if (convertTarget.isBlank() || limit <= 0) return result

        val entries = emojiDictionarySearch
            ?.searchDicdataInputPrefix(convertTarget, limit)
            ?.takeIf { it.isNotEmpty() }
            ?: kanaKanjiEngine.searchEmojiDictionaryEntries(convertTarget, limit)
        if (entries.isEmpty()) return result

        val existingSurfaces = buildSet {
            result.mainResults.forEach { add(it.string) }
            result.supplementaryCandidates.forEach { add(it.string) }
        }
        val emojiCandidates = entries.asSequence()
            .filter { AzooKeyDictionaryMetadata.EmojiVariation !in it.metadata }
            .filter { it.surface !in existingSurfaces }
            .distinctBy { it.surface }
            .take(limit)
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.EMOJI_LEGACY,
                    connectionIdResolver = AzooKeyDictionaryConnectionIdPolicies.Default,
                )
            }
            .toList()
        if (emojiCandidates.isEmpty()) return result
        return result.copy(
            supplementaryCandidates = result.supplementaryCandidates + emojiCandidates,
        )
    }
}
