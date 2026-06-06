package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class CandidateSourceRecord(
    val text: String,
    val reading: String,
    val score: Int,
    val value: AzooKeyPValue = score.toFloat(),
    val azooKeyEntry: AzooKeyDictionaryEntry? = null,
) {
    val dictionaryEntry: AzooKeyDictionaryEntry
        get() = azooKeyEntry ?: AzooKeyDictionaryEntry(
                surface = text,
                reading = reading,
                leftId = null,
                rightId = null,
                mid = AzooKeyMid.GENERAL,
                wordCost = score,
                value = value,
                sourceKind = AzooKeyDictionarySourceKind.System,
            )

    fun toCandidate(type: Byte): Candidate {
        return dictionaryEntry.toCandidate(
            type = type,
            connectionIdResolver = AzooKeyDictionaryConnectionIdResolver(),
        )
    }
}

data class AzooKeyCandidateSourceRecord(
    val dictionaryEntry: AzooKeyDictionaryEntry,
) {
    fun toLegacyRecord(): CandidateSourceRecord {
        return CandidateSourceRecord(
            text = dictionaryEntry.surface,
            reading = dictionaryEntry.reading,
            score = dictionaryEntry.wordCost,
            value = dictionaryEntry.value,
            azooKeyEntry = dictionaryEntry,
        )
    }
}

data class SystemCandidateSourceResult(
    val candidates: List<Candidate>,
    val bunsetsuResult: BunsetsuCandidateResult? = null,
)

typealias SystemKanaKanjiCandidateSourceProvider = EngineSystemDictionarySourceProvider

@Deprecated(
    message = "Use AzooKeyMemoryDictionarySourceProvider",
    replaceWith = ReplaceWith("AzooKeyMemoryDictionarySourceProvider"),
)
class LearnedCandidateSourceProvider(
    private val prefixMatchThreshold: Int,
    private val limit: Int,
    private val search: suspend (input: String, limit: Int) -> List<CandidateSourceRecord>,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (!request.shouldReadMemoryDictionary || request.input.length <= prefixMatchThreshold) {
            return CandidateSources()
        }
        val candidates = search(request.input, limit)
            .map { record -> record.toCandidate(CandidateType.LEARNED_HISTORY) }
            .sortedByDescending { it.value }
        return CandidateSources(memory = candidates)
    }
}

class UserDictionaryCandidateSourceProvider(
    private val prefixMatchThreshold: Int,
    private val limit: Int,
    private val search: suspend (input: String, limit: Int) -> List<CandidateSourceRecord>,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (!request.useUserDictionary || request.input.length <= prefixMatchThreshold) {
            return CandidateSources()
        }
        val candidates = search(request.input, limit)
            .map { record -> record.toCandidate(CandidateType.USER_DICTIONARY) }
            .sortedByDescending { it.value }
        return CandidateSources(userDictionary = candidates)
    }
}

class SystemUserDictionaryCandidateSourceProvider(
    private val prefixMatchThreshold: Int,
    private val limit: Int,
    private val search: suspend (input: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (!request.useUserDictionary || request.input.length <= prefixMatchThreshold) {
            return CandidateSources()
        }
        val candidates = search(request.input, limit)
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.USER_DICTIONARY,
                    connectionIdResolver = AzooKeyDictionaryConnectionIdResolver(),
                )
            }
            .sortedByDescending { it.value }
        return CandidateSources(userDictionary = candidates)
    }
}

class AzooKeyLoudsDictionaryCandidateSourceProvider(
    private val lookup: AzooKeyLoudsDictionarySearcher,
    private val limit: Int,
    private val maxPrefixDepth: Int = Int.MAX_VALUE,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (request.input.isBlank()) {
            return CandidateSources()
        }
        val exactCandidates = lookup.exactEntries(request.input)
            .distinctBy { entry -> entry.reading to entry.surface }
            .sortedByDescending { entry -> entry.value }
            .take(limit)
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.NBEST,
                    connectionIdResolver = AzooKeyDictionaryConnectionIdResolver(),
                )
            }

        val predictionCandidates = if (request.effectiveJapanesePredictionMode.isEnabled) {
            lookup.prefixEntries(
                reading = request.input,
                maxDepth = maxPrefixDepth,
                maxCount = limit,
            )
        } else {
            emptyList()
        }
            .distinctBy { entry -> entry.reading to entry.surface }
            .sortedByDescending { entry -> entry.value }
            .take(limit)
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.NBEST,
                    connectionIdResolver = AzooKeyDictionaryConnectionIdResolver(),
                )
            }

        return CandidateSources(
            system = exactCandidates,
            systemPrediction = predictionCandidates,
        )
    }
}

class EmojiDictionaryCandidateSourceProvider(
    private val limit: Int,
    private val search: suspend (input: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        val candidates = search(request.input, limit)
            .filter { entry -> entry.sourceKind == AzooKeyDictionarySourceKind.Emoji }
            .sortedWith(
                compareByDescending<AzooKeyDictionaryEntry> {
                    AzooKeyDictionaryMetadata.EmojiDicdata in it.metadata
                }.thenByDescending { it.value }
            )
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.EMOJI_SPECIAL,
                    connectionIdResolver = AzooKeyDictionaryConnectionIdPolicies.Default,
                )
            }
        return CandidateSources(special = candidates)
    }
}

class SymbolDictionaryCandidateSourceProvider(
    private val limit: Int,
    private val search: suspend (input: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        val candidates = search(request.input, limit)
            .filter { entry -> entry.sourceKind == AzooKeyDictionarySourceKind.Symbol }
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.SYMBOL_SPECIAL,
                    connectionIdResolver = AzooKeyDictionaryConnectionIdPolicies.Default,
                )
            }
            .sortedByDescending { it.value }
        return CandidateSources(special = candidates)
    }
}

class UserTemplateCandidateSourceProvider(
    private val limit: Int,
    private val search: suspend (input: String, limit: Int) -> List<CandidateSourceRecord>,
    private val candidateType: Byte = USER_TEMPLATE_CANDIDATE_TYPE,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (!request.useUserTemplate) {
            return CandidateSources()
        }
        val candidates = search(request.input, limit)
            .map { record -> record.toCandidate(candidateType) }
            .sortedByDescending { it.value }
        return CandidateSources(userTemplate = candidates)
    }
}

class RomajiCandidateSourceProvider(
    private val romanize: (input: String) -> String?,
    private val toHankakuAlphabet: (input: String) -> String,
) : SuspendCandidateSourceProvider {
    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (!request.useRomajiCandidates) {
            return CandidateSources()
        }
        val conversionString = romanize(request.input) ?: return CandidateSources()
        val conversionFirstCapitalString = conversionString.replaceFirstChar { it.uppercaseChar() }
        val conversionFirstCapitalStringHankaku = toHankakuAlphabet(conversionFirstCapitalString)
        val conversionHankaku = toHankakuAlphabet(conversionString)
        val candidates = listOf(
            Candidate(
                string = conversionString,
                type = USER_TEMPLATE_CANDIDATE_TYPE,
                length = request.input.length.toUByte(),
                score = 29000,
            ),
            Candidate(
                string = conversionHankaku,
                type = ROMAJI_HANKAKU_CANDIDATE_TYPE,
                length = request.input.length.toUByte(),
                score = 29001,
            ),
            Candidate(
                string = conversionFirstCapitalString,
                type = USER_TEMPLATE_CANDIDATE_TYPE,
                length = request.input.length.toUByte(),
                score = 29002,
            ),
            Candidate(
                string = conversionFirstCapitalStringHankaku,
                type = ROMAJI_HANKAKU_CANDIDATE_TYPE,
                length = request.input.length.toUByte(),
                score = 29003,
            ),
            Candidate(
                string = conversionString.uppercase(),
                type = USER_TEMPLATE_CANDIDATE_TYPE,
                length = request.input.length.toUByte(),
                score = 29004,
            ),
            Candidate(
                string = conversionHankaku.uppercase(),
                type = ROMAJI_HANKAKU_CANDIDATE_TYPE,
                length = request.input.length.toUByte(),
                score = 29005,
            ),
        )
        return CandidateSources(romaji = candidates)
    }

    private companion object {
        const val USER_TEMPLATE_CANDIDATE_TYPE: Byte = 30
        const val ROMAJI_HANKAKU_CANDIDATE_TYPE: Byte = 31
    }
}

private const val USER_TEMPLATE_CANDIDATE_TYPE: Byte = 30
