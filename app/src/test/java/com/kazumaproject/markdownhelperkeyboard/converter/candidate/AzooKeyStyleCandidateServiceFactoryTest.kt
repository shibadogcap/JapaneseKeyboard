package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyStyleCandidateServiceFactoryTest {
    @Test
    fun factoryCreatesAuxiliaryProviderWithConfiguredLimitsAndThresholds() = runTest {
        val calls = mutableListOf<String>()
        val factory = AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = 2,
                userDictionaryPrefixMatchThreshold = 1,
                learnedLimit = 3,
                userDictionaryLimit = 5,
                emojiDictionaryLimit = 6,
                symbolDictionaryLimit = 2,
                userTemplateLimit = 7,
            ),
            searchMemory = { input, limit ->
                calls += "memory:$input:$limit"
                listOf(
                    AzooKeyDictionaryEntryMapper.memory(
                        surface = "学習",
                        reading = input,
                        leftId = null,
                        rightId = null,
                        legacyScore = 30,
                        readingLength = input.length,
                    )
                )
            },
            searchUserDictionary = { input, limit ->
                calls += "userDictionary:$input:$limit"
                listOf(CandidateSourceRecord("辞書", input, 20))
            },
            searchSystemUserDictionary = { input, limit ->
                calls += "systemUserDictionary:$input:$limit"
                listOf(
                    AzooKeyDictionaryEntryMapper.systemUserDictionary(
                        surface = "システムユーザー辞書",
                        reading = input,
                        score = 15,
                        leftId = 10,
                        rightId = 11,
                    )
                )
            },
            searchEmojiDictionary = { input, limit ->
                calls += "emojiDictionary:$input:$limit"
                listOf(AzooKeyDictionaryEntryMapper.emoji(surface = "🍣", reading = input))
            },
            searchSymbolDictionary = { input, limit ->
                calls += "symbolDictionary:$input:$limit"
                listOf(AzooKeyDictionaryEntryMapper.symbol(surface = "※", reading = input))
            },
            searchUserTemplate = { input, limit ->
                calls += "userTemplate:$input:$limit"
                listOf(CandidateSourceRecord("定型文", input, 10))
            },
            romanize = { "azookey" },
            toHankakuAlphabet = { it },
        )

        val shortSources = factory.createAuxiliarySourceProvider().provide(baseRequest(input = "あ"))
        val sources = factory.createAuxiliarySourceProvider().provide(baseRequest(input = "あずー"))

        assertEquals(emptyList<Candidate>(), shortSources.memory)
        assertEquals(emptyList<Candidate>(), shortSources.userDictionary)
        assertEquals(listOf("定型文"), shortSources.userTemplate.map { it.string })
        assertEquals(listOf("🍣", "※"), shortSources.special.map { it.string })
        assertEquals(
            listOf(
                "userTemplate:あ:7",
                "emojiDictionary:あ:6",
                "symbolDictionary:あ:2",
                "memory:あずー:3",
                "userTemplate:あずー:7",
                "userDictionary:あずー:5",
                "systemUserDictionary:あずー:12",
                "emojiDictionary:あずー:6",
                "symbolDictionary:あずー:2",
            ),
            calls,
        )
        assertEquals(listOf("学習"), sources.memory.map { it.string })
        assertEquals(listOf("辞書", "システムユーザー辞書"), sources.userDictionary.map { it.string })
        assertEquals(listOf(AzooKeyCid.PROPER_NOUN.toShort(), 10.toShort()), sources.userDictionary.map { it.leftId })
        assertEquals(listOf("🍣", "※"), sources.special.map { it.string })
        assertEquals(listOf(CandidateType.EMOJI_SPECIAL, CandidateType.SYMBOL_SPECIAL), sources.special.map { it.type })
        assertEquals(listOf("azookey", "azookey", "Azookey", "Azookey", "AZOOKEY", "AZOOKEY"), sources.romaji.map { it.string })
    }

    @Test
    fun factoryCreatesCandidateService() = runTest {
        val factory = AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = 0,
                userDictionaryPrefixMatchThreshold = 0,
            ),
            searchMemory = { input, _ ->
                listOf(
                    AzooKeyDictionaryEntryMapper.memory(
                        surface = "学習",
                        reading = input,
                        leftId = null,
                        rightId = null,
                        legacyScore = 30,
                        readingLength = input.length,
                    )
                )
            },
            searchUserDictionary = { _, _ -> emptyList() },
            searchUserTemplate = { _, _ -> emptyList() },
            romanize = { null },
            toHankakuAlphabet = { it },
        )
        val service = factory.create(
            systemSourceProvider = SystemKanaKanjiCandidateSourceProvider(
                convertNormal = {
                    SystemCandidateSourceResult(listOf(candidate("変換", CandidateType.NBEST, score = 10)))
                },
                convertOriginal = { SystemCandidateSourceResult(emptyList()) },
                convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
            )
        )

        val result = service.convert(baseRequest())

        assertEquals(
            setOf("学習", "変換"),
            result.conversionResult.mainResults.map { it.string }.toSet(),
        )
    }

    @Test
    fun factoryAddsLoudsDictionarySearcherToAuxiliaryProvider() = runTest {
        val calls = mutableListOf<String>()
        val factory = AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = 1,
                userDictionaryPrefixMatchThreshold = 1,
                loudsDictionaryLimit = 3,
                loudsDictionaryMaxPrefixDepth = 2,
            ),
            searchMemory = { _, _ -> emptyList() },
            searchUserDictionary = { _, _ -> emptyList() },
            loudsDictionaryLookups = listOf(
                object : AzooKeyLoudsDictionarySearcher {
                    override fun exactEntries(reading: String): List<AzooKeyDictionaryEntry> {
                        calls += "exact:$reading"
                        return listOf(
                            AzooKeyDictionaryEntryMapper.systemDictionary(
                                surface = "司会",
                                reading = reading,
                                wordCost = -10,
                                leftId = 1,
                                rightId = 1,
                                mid = 501,
                            )
                        )
                    }

                    override fun prefixEntries(
                        reading: String,
                        maxDepth: Int,
                        maxCount: Int,
                    ): List<AzooKeyDictionaryEntry> {
                        calls += "prefix:$reading:$maxDepth:$maxCount"
                        return emptyList()
                    }
                }
            ),
            searchUserTemplate = { _, _ -> emptyList() },
            romanize = { null },
            toHankakuAlphabet = { it },
        )

        val sources = factory.createAuxiliarySourceProvider().provide(baseRequest(input = "しかい"))

        assertEquals(listOf("司会"), sources.system.map { it.string })
        assertEquals(emptyList<Candidate>(), sources.systemPrediction)
        assertEquals(listOf("しかい"), sources.system.map { it.yomi })
        assertEquals(listOf("exact:しかい", "prefix:しかい:2:3"), calls)
    }

    private fun baseRequest(input: String = "あずーきー"): CandidateRequest {
        return CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = 10,
            useUserDictionary = true,
            useUserTemplate = true,
            useRomajiCandidates = true,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
            specialCandidateProviders = emptyList(),
        )
    }

    private fun candidate(
        string: String,
        type: Byte,
        score: Int,
    ): Candidate {
        return Candidate(
            string = string,
            type = type,
            length = string.length.toUByte(),
            score = score,
        )
    }
}
