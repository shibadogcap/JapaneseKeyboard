package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import com.kazumaproject.markdownhelperkeyboard.repository.SystemUserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeConverter
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultCandidateService @Inject constructor(
    private val learnRepository: LearnRepository,
    private val learningMemoryRepository: AzooKeyLearningMemoryRepository,
    private val userDictionaryRepository: UserDictionaryRepository,
    private val systemUserDictionaryRepository: SystemUserDictionaryRepository,
    private val userTemplateRepository: UserTemplateRepository,
    private val kanaKanjiEngine: KanaKanjiEngine,
    private val azooKeyDictionaryAssets: AzooKeyDictionaryAssetProvider,
    private val candidateOrderOverrideRepository: CandidateOrderOverrideRepository,
) : CandidateService {

    override suspend fun convert(
        request: CandidateRequest,
        environment: ImeCandidateEnvironment,
    ): AzooKeyStyleCandidateServiceResult {
        val policy = resolveSystemDictionarySourcePolicy(
            loudsRegistryAvailable = azooKeyDictionaryAssets.loudsDictionaryRegistry != null,
            connectionCostStoreAvailable = azooKeyDictionaryAssets.connectionCostStore != null,
            explicit = environment.systemDictionarySourcePolicy,
        )
        return createFactory(environment)
            .create(
                systemSourceProvider = createSystemSourceProvider(environment, policy),
                includeLoudsInAuxiliary = policy == SystemDictionarySourcePolicy.DualPath,
                includeMemoryInAuxiliary = policy != SystemDictionarySourcePolicy.AzooKeyLatticePrimary,
            )
            .convert(request)
    }

    override suspend fun postProcess(
        input: String,
        candidates: List<Candidate>,
        environment: CandidatePostProcessEnvironment,
    ): List<Candidate> {
        return CandidatePostProcessor(
            isNgWordEnabled = environment.isNgWordEnabled,
            ngWordPattern = environment.ngWordPattern,
            isOrderOverrideEnabled = environment.isOrderOverrideEnabled,
            applyOrderOverride = { orderInput, orderCandidates ->
                withContext(Dispatchers.IO) {
                    candidateOrderOverrideRepository.applyOrder(
                        input = orderInput,
                        candidates = orderCandidates,
                    )
                }
            },
        ).process(input, candidates)
    }

    private fun createFactory(
        environment: ImeCandidateEnvironment,
    ): AzooKeyStyleCandidateServiceFactory {
        return AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = environment.auxiliaryConfig,
            searchMemory = { reading, limit ->
                withContext(Dispatchers.IO) {
                    learningMemoryRepository.prefixSearch(reading, limit)
                }
            },
            searchUserTemplate = { input, limit ->
                withContext(Dispatchers.IO) {
                    userTemplateRepository.searchByReading(
                        reading = input,
                        limit = limit,
                    ).map {
                        CandidateSourceRecord(
                            text = it.word,
                            reading = it.reading,
                            score = it.posScore,
                            azooKeyEntry = AzooKeyDictionaryEntryMapper.template(
                                surface = it.word,
                                reading = it.reading,
                                score = it.posScore,
                            ),
                        )
                    }
                }
            },
            searchUserDictionary = { input, limit ->
                withContext(Dispatchers.IO) {
                    userDictionaryRepository.searchByReadingPrefixSuspend(
                        prefix = input,
                        limit = limit,
                    ).map {
                        CandidateSourceRecord(
                            text = it.word,
                            reading = it.reading,
                            score = it.posScore,
                            azooKeyEntry = AzooKeyDictionaryEntryMapper.userDictionary(
                                surface = it.word,
                                reading = it.reading,
                                score = it.posScore,
                                legacyPosIndex = it.posIndex,
                            ),
                        )
                    }
                }
            },
            searchSystemUserDictionary = { input, limit ->
                withContext(Dispatchers.IO) {
                    systemUserDictionaryRepository.searchByReadingPrefix(
                        prefix = input,
                        limit = limit,
                    ).map {
                        AzooKeyDictionaryEntryMapper.systemUserDictionary(
                            surface = it.tango,
                            reading = it.yomi,
                            score = it.score,
                            leftId = it.leftId,
                            rightId = it.rightId,
                        )
                    }
                }
            },
            loudsDictionaryLookups = listOfNotNull(azooKeyDictionaryAssets.loudsDictionaryRegistry),
            searchEmojiDictionary = { input, limit ->
                withContext(Dispatchers.Default) {
                    azooKeyDictionaryAssets.emojiDictionarySearch?.searchInputPrefix(input, limit)
                        ?: kanaKanjiEngine.searchEmojiDictionaryEntries(
                            input = input,
                            limit = limit,
                        )
                }
            },
            searchSymbolDictionary = { input, limit ->
                withContext(Dispatchers.Default) {
                    kanaKanjiEngine.searchSymbolDictionaryEntries(
                        input = input,
                        limit = limit,
                    )
                }
            },
            romanize = environment.romanize,
            toHankakuAlphabet = environment.toHankakuAlphabet,
        )
    }

    private fun createSystemSourceProvider(
        environment: ImeCandidateEnvironment,
        policy: SystemDictionarySourcePolicy,
    ): SystemDictionarySourceProvider {
        val useEngineLearnRepository = environment.isLearnDictionaryMode &&
            policy != SystemDictionarySourcePolicy.AzooKeyLatticePrimary
        val engineProvider = SystemKanaKanjiEngineSourceFactory(
            kanaKanjiEngine = kanaKanjiEngine,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = if (useEngineLearnRepository) learnRepository else null,
            config = environment.systemEngineConfig,
            onNormalBunsetsuResult = environment.onNormalBunsetsuResult,
        ).create()
        val registry = azooKeyDictionaryAssets.loudsDictionaryRegistry
            ?: return engineProvider
        if (policy == SystemDictionarySourcePolicy.AzooKeyLatticePrimary) {
            val connectionStore = azooKeyDictionaryAssets.connectionCostStore
            return LatticePrimarySystemDictionarySourceProvider(
                loudsLookups = listOf(registry),
                searchMemory = { reading, limit ->
                    learningMemoryRepository.prefixSearch(reading, limit)
                },
                engine = engineProvider,
                nBest = environment.auxiliaryConfig.loudsDictionaryLimit,
                latticeConverter = AzooKeyLatticeConverter(
                    decoder = AzooKeyLatticeDecoder(
                        connectionCost = { formerRightId, latterLeftId ->
                            connectionStore?.getConnectionCost(formerRightId, latterLeftId)
                                ?: AzooKeyLatticeDecoder.DEFAULT_CONNECTION_COST
                        },
                        morphologicalCost = { formerMid, latterMid ->
                            connectionStore?.getMorphologicalCost(formerMid, latterMid) ?: 0f
                        },
                    ),
                    typoSearchers = registry.typoSearchers(),
                ),
                latticeIncrementalState = environment.latticeIncrementalState,
            )
        }
        if (policy != SystemDictionarySourcePolicy.AzooKeyLoudsPrimary) {
            return engineProvider
        }
        val louds = AzooKeyLoudsDictionaryCandidateSourceProvider(
            lookup = registry,
            limit = environment.auxiliaryConfig.loudsDictionaryLimit,
            maxPrefixDepth = environment.auxiliaryConfig.loudsDictionaryMaxPrefixDepth,
        )
        return LoudsPrimarySystemDictionarySourceProvider(
            louds = louds,
            engine = engineProvider,
        )
    }
}