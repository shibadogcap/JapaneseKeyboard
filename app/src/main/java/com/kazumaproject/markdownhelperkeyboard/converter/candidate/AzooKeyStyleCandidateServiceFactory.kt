package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class AuxiliaryCandidateSourceConfig(
    val learnedPrefixMatchThreshold: Int,
    val userDictionaryPrefixMatchThreshold: Int,
    val learnedLimit: Int = 12,
    val userDictionaryLimit: Int = 12,
    val systemUserDictionaryLimit: Int = 12,
    val loudsDictionaryLimit: Int = 32,
    val loudsDictionaryMaxPrefixDepth: Int = 4,
    val emojiDictionaryLimit: Int = 16,
    val symbolDictionaryLimit: Int = 16,
    val userTemplateLimit: Int = 16,
)

class AzooKeyStyleCandidateServiceFactory(
    private val auxiliaryConfig: AuxiliaryCandidateSourceConfig,
    private val searchMemory: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
    private val searchUserDictionary: suspend (input: String, limit: Int) -> List<CandidateSourceRecord>,
    private val searchSystemUserDictionary: suspend (input: String, limit: Int) -> List<AzooKeyDictionaryEntry> = { _, _ -> emptyList() },
    private val loudsDictionaryLookups: List<AzooKeyLoudsDictionarySearcher> = emptyList(),
    private val searchEmojiDictionary: suspend (input: String, limit: Int) -> List<AzooKeyDictionaryEntry> = { _, _ -> emptyList() },
    private val searchSymbolDictionary: suspend (input: String, limit: Int) -> List<AzooKeyDictionaryEntry> = { _, _ -> emptyList() },
    private val searchUserTemplate: suspend (input: String, limit: Int) -> List<CandidateSourceRecord>,
    private val romanize: (input: String) -> String?,
    private val toHankakuAlphabet: (input: String) -> String,
) {
    fun create(
        systemSourceProvider: SystemDictionarySourceProvider,
        includeLoudsInAuxiliary: Boolean = true,
        includeMemoryInAuxiliary: Boolean = true,
    ): AzooKeyStyleCandidateService {
        return AzooKeyStyleCandidateService(
            auxiliarySourceProvider = createAuxiliarySourceProvider(
                includeLoudsInAuxiliary = includeLoudsInAuxiliary,
                includeMemoryInAuxiliary = includeMemoryInAuxiliary,
            ),
            systemSourceProvider = systemSourceProvider,
        )
    }

    fun createAuxiliarySourceProvider(
        includeLoudsInAuxiliary: Boolean = true,
        includeMemoryInAuxiliary: Boolean = true,
    ): SuspendCandidateSourceProvider {
        return CompositeSuspendCandidateSourceProvider(
            buildList {
                if (includeMemoryInAuxiliary) {
                    add(
                        AzooKeyMemoryDictionarySourceProvider(
                            prefixMatchThreshold = auxiliaryConfig.learnedPrefixMatchThreshold,
                            limit = auxiliaryConfig.learnedLimit,
                            search = searchMemory,
                        )
                    )
                }
                add(
                    UserTemplateCandidateSourceProvider(
                        limit = auxiliaryConfig.userTemplateLimit,
                        search = searchUserTemplate,
                    )
                )
                add(
                    UserDictionaryCandidateSourceProvider(
                        prefixMatchThreshold = auxiliaryConfig.userDictionaryPrefixMatchThreshold,
                        limit = auxiliaryConfig.userDictionaryLimit,
                        search = searchUserDictionary,
                    )
                )
                add(
                    SystemUserDictionaryCandidateSourceProvider(
                        prefixMatchThreshold = auxiliaryConfig.userDictionaryPrefixMatchThreshold,
                        limit = auxiliaryConfig.systemUserDictionaryLimit,
                        search = searchSystemUserDictionary,
                    )
                )
                if (includeLoudsInAuxiliary) {
                    loudsDictionaryLookups.forEach { lookup ->
                        add(
                            AzooKeyLoudsDictionaryCandidateSourceProvider(
                                lookup = lookup,
                                limit = auxiliaryConfig.loudsDictionaryLimit,
                                maxPrefixDepth = auxiliaryConfig.loudsDictionaryMaxPrefixDepth,
                            )
                        )
                    }
                }
                add(
                    EmojiDictionaryCandidateSourceProvider(
                        limit = auxiliaryConfig.emojiDictionaryLimit,
                        search = searchEmojiDictionary,
                    )
                )
                add(
                    SymbolDictionaryCandidateSourceProvider(
                        limit = auxiliaryConfig.symbolDictionaryLimit,
                        search = searchSymbolDictionary,
                    )
                )
                add(
                    RomajiCandidateSourceProvider(
                        romanize = romanize,
                        toHankakuAlphabet = toHankakuAlphabet,
                    )
                )
            }
        )
    }
}
