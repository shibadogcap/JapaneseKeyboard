package com.kazumaproject.markdownhelperkeyboard.converter.candidate

class PostCommitEmojiDictionaryProvider(
    private val limit: Int,
    private val search: suspend (committedText: String, committedReading: String?, limit: Int) -> List<AzooKeyDictionaryEntry>,
) {
    suspend fun provide(committedText: String, committedReading: String? = null): List<Candidate> {
        if (committedText.isBlank() || limit <= 0) {
            return emptyList()
        }

        return search(committedText, committedReading, limit)
            .asSequence()
            .filter { it.sourceKind == AzooKeyDictionarySourceKind.Emoji }
            .filter { AzooKeyDictionaryMetadata.EmojiVariation !in it.metadata }
            .distinctBy { it.surface }
            .take(limit)
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.EMOJI_SUFFIX,
                    connectionIdResolver = AzooKeyDictionaryConnectionIdPolicies.Default,
                )
            }
            .toList()
    }
}
