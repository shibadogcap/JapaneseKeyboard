package com.kazumaproject.markdownhelperkeyboard.converter.candidate

class PostCommitEmojiDictionaryProvider(
    private val limit: Int,
    private val textReplacer: AzooKeyTextReplacer = AzooKeyTextReplacer.empty,
    private val fallbackSearch: suspend (
        committedText: String,
        committedReading: String?,
        limit: Int,
    ) -> List<AzooKeyDictionaryEntry> = { _, _, _ -> emptyList() },
) {
    suspend fun provide(
        leftSideCandidate: Candidate,
        committedReading: String? = null,
    ): List<Candidate> {
        if (leftSideCandidate.string.isBlank() || limit <= 0) {
            return emptyList()
        }

        if (!textReplacer.isEmpty) {
            return AzooKeyPostCommitEmojiCollector.collect(
                leftSideCandidate = leftSideCandidate,
                textReplacer = textReplacer,
                limit = limit,
            )
        }

        return fallbackSearch(leftSideCandidate.string, committedReading, limit)
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
