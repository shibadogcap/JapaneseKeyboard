package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyDicdataStoreUtils

/**
 * AzooKey [KanaKanjiConverter.requestPostCompositionPredictionCandidates](https://github.com/azooKey/AzooKeyKanaKanjiConverter)
 * の TextReplacer 絵文字収集ロジック相当。
 */
object AzooKeyPostCommitEmojiCollector {
    fun collect(
        leftSideCandidate: Candidate,
        textReplacer: AzooKeyTextReplacer,
        limit: Int = 8,
    ): List<Candidate> {
        if (textReplacer.isEmpty || limit <= 0) return emptyList()

        val merged = linkedMapOf<String, Candidate>()
        for (entry in leftSideCandidate.data) {
            if (!AzooKeyDicdataStoreUtils.includeMMValueCalculation(entry.leftId, entry.rightId)) {
                continue
            }
            for (result in textReplacer.getSearchResult(entry.surface, ignoreNonBaseEmoji = true)) {
                if (merged.size >= limit) break
                merged.getOrPut(result.text) {
                    result.toPostCommitCandidate()
                }
            }
        }
        return merged.values.toList()
    }

    private fun AzooKeyTextReplacer.SearchResultItem.toPostCommitCandidate(): Candidate {
        val entry = AzooKeyDictionaryEntryMapper.emoji(
            surface = text,
            reading = "エモジ",
            score = -3,
        )
        return entry.toCandidate(
            type = CandidateType.EMOJI_SUFFIX,
            connectionIdResolver = AzooKeyDictionaryConnectionIdPolicies.Default,
        )
    }
}
