package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.dictionary.models.TokenEntryConverted
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateSourceAdaptersTest {
    @Test
    fun systemProviderDispatchesByRequestModeAndStoresBunsetsuResult() = runTest {
        val bunsetsuResult = BunsetsuCandidateResult(
            candidates = listOf(candidate("通常", CandidateType.NBEST, score = 10)),
            splitPatterns = listOf(listOf(2)),
            splitPatternByCandidateString = mapOf("通常" to listOf(2))
        )
        val provider = SystemKanaKanjiCandidateSourceProvider(
            convertNormal = { SystemCandidateSourceResult(bunsetsuResult.candidates, bunsetsuResult) },
            convertOriginal = { SystemCandidateSourceResult(listOf(candidate("原文", CandidateType.NBEST, score = 20))) },
            convertWithoutPrediction = {
                SystemCandidateSourceResult(listOf(candidate("予測なし", CandidateType.NBEST, score = 30)))
            }
        )

        val normalSources = provider.provide(baseRequest(mode = CandidateRequestMode.Normal))
        val originalSources = provider.provide(baseRequest(mode = CandidateRequestMode.Original))
        val withoutPredictionSources = provider.provide(baseRequest(mode = CandidateRequestMode.WithoutPrediction))

        assertEquals(listOf("通常"), normalSources.system.map { it.string })
        assertEquals(listOf(2), bunsetsuResult.primarySplitPositions)
        assertEquals(listOf("原文"), originalSources.system.map { it.string })
        assertEquals(listOf("予測なし"), withoutPredictionSources.system.map { it.string })
    }

    @Test
    fun systemProviderReturnsEmptyForEnglishKanaMode() = runTest {
        val provider = SystemKanaKanjiCandidateSourceProvider(
            convertNormal = { error("normal should not be called") },
            convertOriginal = { error("original should not be called") },
            convertWithoutPrediction = { error("without prediction should not be called") },
        )

        val sources = provider.provide(baseRequest(mode = CandidateRequestMode.EnglishKana))

        assertEquals(emptyList<Candidate>(), sources.system)
        assertEquals(null, provider.lastBunsetsuResult)
    }

    @Test
    fun learnedProviderSkipsPrivateRequests() = runTest {
        var searchCount = 0
        val provider = LearnedCandidateSourceProvider(
            prefixMatchThreshold = 1,
            limit = 4,
            search = { _, _ ->
                searchCount += 1
                listOf(CandidateSourceRecord(text = "明日", reading = "あした", score = 100))
            }
        )

        val sources = provider.provide(
            baseRequest(
                input = "あした",
                privacy = CandidateRequestPrivacy(isPrivateMode = true)
            )
        )

        assertEquals(0, searchCount)
        assertEquals(emptyList<Candidate>(), sources.memory)
    }

    @Test
    fun learnedProviderUsesAzooKeyDictionaryEntryWhenPresent() = runTest {
        val provider = LearnedCandidateSourceProvider(
            prefixMatchThreshold = 1,
            limit = 4,
            search = { _, _ ->
                listOf(
                    CandidateSourceRecord(
                        text = "fallback",
                        reading = "fallback",
                        score = 999,
                        azooKeyEntry = AzooKeyDictionaryEntryMapper.learned(
                            surface = "学習候補",
                            reading = "がくしゅう",
                            score = 42,
                            leftId = 1285,
                            rightId = 1285,
                        ),
                    )
                )
            }
        )

        val sources = provider.provide(baseRequest(input = "がくしゅう"))

        assertEquals(listOf("学習候補"), sources.memory.map { it.string })
        assertEquals(listOf(42), sources.memory.map { it.score })
        assertEquals(listOf("がくしゅう".length.toUByte()), sources.memory.map { it.length })
        assertEquals(listOf("がくしゅう"), sources.memory.map { it.yomi })
        assertEquals(listOf(1285.toShort()), sources.memory.map { it.leftId })
        assertEquals(listOf(1285.toShort()), sources.memory.map { it.rightId })
    }

    @Test
    fun userDictionaryProviderUsesPrefixThresholdAndSortsByAzooKeyValue() = runTest {
        val provider = UserDictionaryCandidateSourceProvider(
            prefixMatchThreshold = 1,
            limit = 4,
            search = { input, limit ->
                assertEquals("あず", input)
                assertEquals(4, limit)
                listOf(
                    CandidateSourceRecord(text = "後", reading = "あず", score = -20),
                    CandidateSourceRecord(text = "先", reading = "あず", score = -10),
                )
            }
        )

        val shortSources = provider.provide(baseRequest(input = "あ"))
        val sources = provider.provide(baseRequest(input = "あず"))

        assertEquals(emptyList<Candidate>(), shortSources.userDictionary)
        assertEquals(listOf("先", "後"), sources.userDictionary.map { it.string })
        assertEquals(CandidateType.USER_DICTIONARY, sources.userDictionary.first().type)
        assertEquals(
            listOf(AzooKeyCid.PROPER_NOUN.toShort(), AzooKeyCid.PROPER_NOUN.toShort()),
            sources.userDictionary.map { it.leftId }
        )
    }

    @Test
    fun userDictionaryMapperMatchesAzooKeyDynamicDictionaryDefaults() {
        val entry = AzooKeyDictionaryEntryMapper.userDictionary(
            surface = "蒼鍵",
            reading = "あおかぎ",
            score = -10,
            legacyPosIndex = 7,
        )

        assertEquals("蒼鍵", entry.surface)
        assertEquals("あおかぎ", entry.reading)
        assertEquals(AzooKeyCid.PROPER_NOUN, entry.leftId)
        assertEquals(AzooKeyCid.PROPER_NOUN, entry.rightId)
        assertEquals(AzooKeyMid.GENERAL, entry.mid)
        assertEquals(-10, entry.wordCost)
        assertEquals(AzooKeyDictionarySourceKind.User, entry.sourceKind)
        assertEquals(7, entry.legacyPosIndex)
        assertEquals(setOf(AzooKeyDictionaryMetadata.FromUserDictionary), entry.metadata)
    }

    @Test
    fun userDictionaryWithContextIdMapperKeepsLegacyContextIdsForGraph() {
        val entry = AzooKeyDictionaryEntryMapper.userDictionaryWithContextId(
            surface = "蒼鍵",
            reading = "あおかぎ",
            score = 120,
            contextId = 777,
            legacyPosIndex = 3,
        )
        val node = AzooKeyDictionaryNodeMapper.toNode(
            entry = entry,
            startPosition = 4,
        )

        assertEquals(777.toShort(), node.l)
        assertEquals(777.toShort(), node.r)
        assertEquals(120, node.score)
        assertEquals("蒼鍵", node.tango)
        assertEquals("あおかぎ", node.yomiUsed)
        assertEquals(4, node.sPos)
        assertEquals(3, entry.legacyPosIndex)
    }

    @Test
    fun learnedGraphPolicyKeepsLegacyFallbackContextId() {
        val entry = AzooKeyDictionaryEntryMapper.learned(
            surface = "学習",
            reading = "がくしゅう",
            score = 80,
            leftId = null,
            rightId = null,
        )
        val node = AzooKeyDictionaryNodeMapper.toNode(
            entry = entry,
            startPosition = 0,
            connectionIdResolver = AzooKeyDictionaryConnectionIdPolicies.LearnedGraph,
        )

        assertEquals(1851.toShort(), node.l)
        assertEquals(1851.toShort(), node.r)
        assertEquals(80, node.score)
    }

    @Test
    fun systemDictionaryMapperKeepsConnectionIdsAndCost() {
        val entry = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "明日",
            reading = "あした",
            wordCost = -120,
            leftId = AzooKeyCid.GENERAL_NOUN,
            rightId = AzooKeyCid.GENERAL_NOUN,
            mid = AzooKeyMid.GENERAL,
        )

        assertEquals("明日", entry.surface)
        assertEquals("あした", entry.reading)
        assertEquals(AzooKeyCid.GENERAL_NOUN, entry.leftId)
        assertEquals(AzooKeyCid.GENERAL_NOUN, entry.rightId)
        assertEquals(AzooKeyMid.GENERAL, entry.mid)
        assertEquals(-120, entry.wordCost)
        assertEquals(AzooKeyDictionarySourceKind.System, entry.sourceKind)
    }

    @Test
    fun tokenEntryConvertedMapperKeepsDictionaryIdsAndReadingLength() {
        val entry = AzooKeyDictionaryEntryMapper.tokenEntryConverted(
            reading = "あずーきー",
            tokenEntry = TokenEntryConverted(
                leftId = 10.toShort(),
                rightId = 20.toShort(),
                wordCost = (-30).toShort(),
                tango = "azooKey",
                yomiLength = 3.toShort(),
            ),
        )

        assertEquals("azooKey", entry.surface)
        assertEquals("あずー", entry.reading)
        assertEquals(10, entry.leftId)
        assertEquals(20, entry.rightId)
        assertEquals(-30, entry.wordCost)
        assertEquals(AzooKeyDictionarySourceKind.System, entry.sourceKind)
    }

    @Test
    fun dictionaryNodeMapperBuildsNodeFromTokenEntryConverted() {
        val entry = AzooKeyDictionaryEntryMapper.tokenEntryConverted(
            reading = "あずーきー",
            tokenEntry = TokenEntryConverted(
                leftId = 10.toShort(),
                rightId = 20.toShort(),
                wordCost = (-30).toShort(),
                tango = "azooKey",
                yomiLength = 3.toShort(),
            ),
        )

        val node = AzooKeyDictionaryNodeMapper.toNode(
            entry = entry,
            startPosition = 2,
            scoreOffset = 100,
        )

        assertEquals(10.toShort(), node.l)
        assertEquals(20.toShort(), node.r)
        assertEquals(70, node.score)
        assertEquals(70, node.f)
        assertEquals(70, node.g)
        assertEquals("azooKey", node.tango)
        assertEquals("あずー", node.yomiUsed)
        assertEquals(3.toShort(), node.len)
        assertEquals(2, node.sPos)
    }

    @Test
    fun connectionIdResolverFillsOnlyMissingIds() {
        val resolver = AzooKeyDictionaryConnectionIdResolver(
            defaultLeftId = AzooKeyCid.PROPER_NOUN,
            defaultRightId = AzooKeyCid.GENERAL_NOUN,
        )
        val unresolved = CandidateSourceRecord(
            text = "未解決",
            reading = "みかいけつ",
            score = 100,
        ).dictionaryEntry
        val partiallyResolved = unresolved.copy(leftId = AzooKeyCid.SYMBOL)

        val resolved = resolver.resolve(unresolved)
        val resolvedPartial = resolver.resolve(partiallyResolved)

        assertEquals(AzooKeyCid.PROPER_NOUN, resolved.leftId)
        assertEquals(AzooKeyCid.GENERAL_NOUN, resolved.rightId)
        assertEquals(AzooKeyCid.SYMBOL, resolvedPartial.leftId)
        assertEquals(AzooKeyCid.GENERAL_NOUN, resolvedPartial.rightId)
    }

    @Test
    fun systemUserDictionaryProviderSortsAzooKeyEntriesAndPreservesIds() = runTest {
        val provider = SystemUserDictionaryCandidateSourceProvider(
            prefixMatchThreshold = 1,
            limit = 4,
            search = { input, limit ->
                assertEquals("あず", input)
                assertEquals(4, limit)
                listOf(
                    AzooKeyDictionaryEntryMapper.systemUserDictionary(
                        surface = "後",
                        reading = "あず",
                        score = -20,
                        leftId = 12,
                        rightId = 13,
                    ),
                    AzooKeyDictionaryEntryMapper.systemUserDictionary(
                        surface = "先",
                        reading = "あず",
                        score = -10,
                        leftId = 10,
                        rightId = 11,
                    ),
                )
            }
        )

        val shortSources = provider.provide(baseRequest(input = "あ"))
        val sources = provider.provide(baseRequest(input = "あず"))

        assertEquals(emptyList<Candidate>(), shortSources.userDictionary)
        assertEquals(listOf("先", "後"), sources.userDictionary.map { it.string })
        assertEquals(listOf(-10, -20), sources.userDictionary.map { it.score })
        assertEquals(listOf(10.toShort(), 12.toShort()), sources.userDictionary.map { it.leftId })
        assertEquals(listOf(11.toShort(), 13.toShort()), sources.userDictionary.map { it.rightId })
    }

    @Test
    fun emojiDictionaryProviderReturnsEmojiSpecialCandidates() = runTest {
        val provider = EmojiDictionaryCandidateSourceProvider(
            limit = 3,
            search = { input, limit ->
                assertEquals("えもじ", input)
                assertEquals(3, limit)
                listOf(
                    AzooKeyDictionaryEntryMapper.emoji("😀", "えもじ", score = -5),
                    AzooKeyDictionaryEntryMapper.symbol("☆", "えもじ", score = -10),
                )
            }
        )

        val sources = provider.provide(baseRequest(input = "えもじ"))

        assertEquals(listOf("😀"), sources.special.map { it.string })
        assertEquals(listOf(CandidateType.EMOJI_SPECIAL), sources.special.map { it.type })
        assertEquals(listOf(AzooKeyCid.SYMBOL.toShort()), sources.special.map { it.leftId })
        assertEquals(listOf("えもじ"), sources.special.map { it.yomi })
    }

    @Test
    fun emojiDictionaryProviderKeepsDicdataCostOrdering() = runTest {
        val provider = EmojiDictionaryCandidateSourceProvider(
            limit = 10,
            search = { input, _ ->
                AzooKeyEmojiDictionarySearch.fromAzooKeyEmojiDictionaryTexts(
                    textReplacerText = "😀️\tえがお,笑顔\t",
                    dicdataText = "エガオ\t😄\t5\t5\t501\t-20\nエガオ\t😀️\t5\t5\t501\t-10",
                ).searchInputPrefix(input, limit = 10)
            }
        )

        val sources = provider.provide(baseRequest(input = "えが"))

        assertEquals(listOf("😀️", "😄"), sources.special.map { it.string })
        assertEquals(listOf(-10, -20), sources.special.map { it.score })
    }

    @Test
    fun symbolDictionaryProviderReturnsSymbolSpecialCandidates() = runTest {
        val provider = SymbolDictionaryCandidateSourceProvider(
            limit = 3,
            search = { input, limit ->
                assertEquals("ほし", input)
                assertEquals(3, limit)
                listOf(
                    AzooKeyDictionaryEntryMapper.symbol("☆", "ほし", score = -5),
                    AzooKeyDictionaryEntryMapper.emoji("⭐", "ほし", score = -10),
                )
            }
        )

        val sources = provider.provide(baseRequest(input = "ほし"))

        assertEquals(listOf("☆"), sources.special.map { it.string })
        assertEquals(listOf(CandidateType.SYMBOL_SPECIAL), sources.special.map { it.type })
        assertEquals(listOf(AzooKeyCid.SYMBOL.toShort()), sources.special.map { it.rightId })
        assertEquals(listOf("ほし"), sources.special.map { it.yomi })
    }

    @Test
    fun candidatePostProcessorKeepsUserDictionaryBeforeDuplicatedSystemUserDictionary() = runTest {
        val processor = CandidatePostProcessor(
            isNgWordEnabled = false,
            ngWordPattern = Regex("NG"),
            isOrderOverrideEnabled = false,
            applyOrderOverride = { _, candidates -> candidates },
        )

        val result = processor.process(
            input = "あず",
            candidates = listOf(
                Candidate(
                    string = "蒼鍵",
                    type = CandidateType.USER_DICTIONARY,
                    length = 2.toUByte(),
                    score = 200,
                    yomi = "あず",
                    leftId = 1.toShort(),
                    rightId = 1.toShort(),
                ),
                Candidate(
                    string = "蒼鍵",
                    type = CandidateType.USER_DICTIONARY,
                    length = 2.toUByte(),
                    score = 100,
                    yomi = "あず",
                    leftId = 2.toShort(),
                    rightId = 2.toShort(),
                ),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(1.toShort(), result.first().leftId)
    }

    @Test
    fun userTemplateProviderSkipsWhenDisabled() = runTest {
        var searchCount = 0
        val provider = UserTemplateCandidateSourceProvider(
            limit = 8,
            search = { _, _ ->
                searchCount += 1
                listOf(CandidateSourceRecord(text = "いつもの", reading = "いつもの", score = 100))
            }
        )

        val sources = provider.provide(baseRequest(useUserTemplate = false))

        assertEquals(0, searchCount)
        assertEquals(emptyList<Candidate>(), sources.userTemplate)
    }

    @Test
    fun romajiProviderBuildsAzooKeyStyleVariants() = runTest {
        val provider = RomajiCandidateSourceProvider(
            romanize = { "azookey" },
            toHankakuAlphabet = { it }
        )

        val sources = provider.provide(baseRequest(input = "あずーきー"))

        assertEquals(
            listOf("azookey", "azookey", "Azookey", "Azookey", "AZOOKEY", "AZOOKEY"),
            sources.romaji.map { it.string }
        )
        assertEquals(listOf(29000, 29001, 29002, 29003, 29004, 29005), sources.romaji.map { it.score })
    }

    private fun baseRequest(
        input: String = "あずーきー",
        mode: CandidateRequestMode = CandidateRequestMode.Normal,
        useUserTemplate: Boolean = true,
        privacy: CandidateRequestPrivacy = CandidateRequestPrivacy(),
    ): CandidateRequest {
        return CandidateRequest(
            input = input,
            mode = mode,
            nBest = 10,
            useUserDictionary = true,
            useUserTemplate = useUserTemplate,
            useRomajiCandidates = true,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
            privacy = privacy,
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
