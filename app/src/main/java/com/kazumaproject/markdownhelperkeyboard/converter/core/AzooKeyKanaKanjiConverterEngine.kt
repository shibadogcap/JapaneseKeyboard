package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.CandidateRequestBridge
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConversionResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.parseTemplate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.toFullWidthRoman
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.toHalfWidthKana
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLattice
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeDualIndexMap
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIndex
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeRange
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyMutableLatticeNode
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.CandidateData
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.applyAppropriateActions
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.getClauses
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort
import javax.inject.Inject
import javax.inject.Singleton

data class AzooKeyConverterEngineResult(
    val conversionResult: AzooKeyStyleConversionResult,
    val bunsetsuResult: BunsetsuCandidateResult?,
    val usedAfterComplete: Boolean,
    val lattice: AzooKeyLattice?,
)

@Singleton
class AzooKeyKanaKanjiConverterEngine private constructor(
    private val dicdataFacadeSource: AzooKeyDicdataFacadeSource,
    private val zenzEngine: ZenzEnginePort?,
    private val englishSpellChecker: AzooKeyEnglishSpellChecker?,
) {
    @Inject constructor(
        dicdataFacadeFactory: AzooKeyDicdataFacadeFactory,
        zenzEngine: ZenzEnginePort,
        englishSpellChecker: AzooKeyEnglishSpellChecker,
    ) : this(dicdataFacadeFactory.asSource(), zenzEngine, englishSpellChecker)

    constructor(
        registry: com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionaryRegistry,
        connectionStore: com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore,
    ) : this(azooKeyDicdataFacadeSourceForTests(registry, connectionStore), null, null)

    private data class SessionState(
        var previousInputData: ComposingText? = null,
        var lattice: AzooKeyLattice = AzooKeyLattice(),
        var completedData: Candidate? = null,
        var zenzaiCache: AzooKeyZenzaiCache? = null,
        var stablePredictionCache: StablePredictionCandidateCacheEntry? = null,
    )

    private val sessions = mutableMapOf<String, SessionState>()

    suspend fun requestCandidates(
        inputData: ComposingText,
        options: ConvertRequestOptions,
        session: ConversionSession,
        searchMemory: suspend (reading: String, limit: Int) -> List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry>,
        searchUserTemplate: suspend (input: String, limit: Int) -> List<Candidate>,
    ): AzooKeyConverterEngineResult {
        if (inputData.isEmpty) {
            return emptyResult()
        }

        val sessionState = sessions.getOrPut(session.sessionId) { SessionState() }
        session.completedCandidate?.let { sessionState.completedData = it }

        val facade = dicdataFacadeSource.create(searchMemory)
        val kana2Kanji = AzooKeyKana2Kanji(facade)
        val needTypo = options.typoCorrectionMode != AzooKeyStyleTypoCorrectionMode.Disabled
        val useMemory = options.learningType != AzooKeyStyleLearningType.Nothing

        val convertResult = convertToLattice(
            inputData = inputData,
            options = options,
            session = session,
            sessionState = sessionState,
            kana2Kanji = kana2Kanji,
            needTypoCorrection = needTypo,
            useMemory = useMemory,
        ) ?: return emptyResult()

        val conversionResult = processResult(
            inputData = inputData,
            latticeResult = convertResult.latticeResult,
            options = options,
            kana2Kanji = kana2Kanji,
            sessionState = sessionState,
            searchUserTemplate = searchUserTemplate,
        )

        return AzooKeyConverterEngineResult(
            conversionResult = conversionResult,
            bunsetsuResult = buildBunsetsuResult(conversionResult.mainResults),
            usedAfterComplete = convertResult.usedAfterComplete,
            lattice = convertResult.latticeResult.second,
        )
    }

    fun stopComposition(sessionId: String = ConversionSession.DEFAULT_SESSION_ID, keepCompletedData: Boolean = false) {
        if (keepCompletedData) {
            val completed = sessions[sessionId]?.completedData
            sessions[sessionId] = SessionState(completedData = completed)
        } else {
            sessions.remove(sessionId)
        }
    }

    fun setCompletedData(sessionId: String, candidate: Candidate) {
        sessions.getOrPut(sessionId) { SessionState() }.completedData = candidate
    }

    private data class ConvertToLatticeResult(
        val latticeResult: Pair<AzooKeyMutableLatticeNode, AzooKeyLattice>,
        val usedAfterComplete: Boolean,
    )

    private suspend fun convertToLattice(
        inputData: ComposingText,
        options: ConvertRequestOptions,
        session: ConversionSession,
        sessionState: SessionState,
        kana2Kanji: AzooKeyKana2Kanji,
        needTypoCorrection: Boolean,
        useMemory: Boolean,
    ): ConvertToLatticeResult? {
        if (options.zenzaiMode.isEnabled) {
            val zenzai = AzooKeyZenzaiConverter(
                kana2Kanji = kana2Kanji,
                zenzEngine = zenzEngine,
                zenzProfile = options.zenzProfile,
            )
            val result = zenzai.allZenzai(
                inputData = inputData,
                options = options,
                cache = sessionState.zenzaiCache,
                useMemory = useMemory,
                leftSideContext = session.leftSideContext.takeLast(
                    com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyConversionDefaults.ZENZ_LEFT_CONTEXT_MAX,
                ),
            )
            sessionState.previousInputData = inputData
            sessionState.zenzaiCache = result.cache
            return ConvertToLatticeResult(result.result to result.lattice, usedAfterComplete = false)
        }

        val previousInputData = sessionState.previousInputData
        if (previousInputData == null) {
            val result = kana2Kanji.kana2latticeAll(
                inputData = inputData,
                nBest = options.nBest,
                needTypoCorrection = needTypoCorrection,
                useMemory = useMemory,
            )
            sessionState.previousInputData = inputData
            return ConvertToLatticeResult(result, usedAfterComplete = false)
        }

        if (previousInputData == inputData) {
            val result = kana2Kanji.kana2latticeNoChange(previousInputData to sessionState.lattice)
            sessionState.previousInputData = inputData
            return ConvertToLatticeResult(result, usedAfterComplete = false)
        }

        val completedData = sessionState.completedData
        if (completedData != null && previousInputData.inputHasSuffix(inputData)) {
            val result = kana2Kanji.kana2latticeAfterComplete(
                inputData = inputData,
                completedData = completedData,
                nBest = options.nBest,
                previousResult = previousInputData to sessionState.lattice,
                useMemory = useMemory,
                needTypoCorrection = needTypoCorrection,
            )
            sessionState.previousInputData = inputData
            sessionState.completedData = null
            return ConvertToLatticeResult(result, usedAfterComplete = true)
        }

        val diff = inputData.differenceSuffix(previousInputData)
        val result = kana2Kanji.kana2latticeChanged(
            inputData = inputData,
            nBest = options.nBest,
            counts = diff,
            previousResult = previousInputData to sessionState.lattice,
            needTypoCorrection = needTypoCorrection,
            useMemory = useMemory,
        )
        sessionState.previousInputData = inputData
        return ConvertToLatticeResult(result, usedAfterComplete = false)
    }

    private suspend fun processResult(
        inputData: ComposingText,
        latticeResult: Pair<AzooKeyMutableLatticeNode, AzooKeyLattice>,
        options: ConvertRequestOptions,
        kana2Kanji: AzooKeyKana2Kanji,
        sessionState: SessionState,
        searchUserTemplate: suspend (input: String, limit: Int) -> List<Candidate>,
    ): AzooKeyStyleConversionResult {
        sessionState.lattice = latticeResult.second
        val useMemory = options.learningType != AzooKeyStyleLearningType.Nothing

        val clauseResult = kana2Kanji.getCandidateDataFromResult(latticeResult.first)
        if (clauseResult.isEmpty()) {
            sessionState.stablePredictionCache = null
            val additional = getAdditionalCandidate(inputData, options)
            return AzooKeyStyleConversionResult(mainResults = additional, firstClauseResults = additional)
        }

        val wholeSentenceUniqueCandidates: List<Candidate>
        var bestCandidateDataForPrediction: CandidateData? = null
        if (options.requireJapanesePrediction.isEnabled) {
            val mapped = clauseResult.map { kana2Kanji.processClauseCandidate(it) }
            bestCandidateDataForPrediction = clauseResult.zip(mapped).maxByOrNull { it.second.value }?.first
            wholeSentenceUniqueCandidates = getUniqueCandidate(mapped)
        } else {
            wholeSentenceUniqueCandidates = getUniqueCandidate(
                clauseResult.map { kana2Kanji.processClauseCandidate(it) },
            )
        }

        val userShortcutsCandidates = buildUserShortcutsCandidates(
            inputData = inputData,
            kana2Kanji = kana2Kanji,
            searchUserTemplate = searchUserTemplate,
            options = options,
        )

        if (options.requestQuery == ConvertRequestOptions.RequestQuery.ExactMatch) {
            sessionState.stablePredictionCache = null
            val merged = getUniqueCandidate(wholeSentenceUniqueCandidates + userShortcutsCandidates)
            val mainResults = if (options.zenzaiMode.isEnabled) {
                merged
            } else {
                merged.sortedByDescending { it.value }
            }
            return AzooKeyStyleConversionResult(mainResults = mainResults.map { it.applyAppropriateActions().parseTemplate() })
        }

        val bestFiveSentenceCandidates = if (options.zenzaiMode.isEnabled) {
            AzooKeyZenzaiValueReorder.reorderTopValues(wholeSentenceUniqueCandidates.take(5))
        } else {
            wholeSentenceUniqueCandidates.sortedByDescending { it.value }.take(5)
        }

        val predictiveSource = AzooKeyPredictiveInputResolver.resolve(
            inputData,
            options.roman2KanaTransducer,
        )

        var predictionResults = emptyList<Candidate>()
        var englishPredictionResults = emptyList<Candidate>()
        var stablePredictionCandidates = sessionState.stablePredictionCache
            ?.compatibleCandidates(
                currentConvertTarget = inputData.convertTarget,
                baseConvertTarget = predictiveSource.baseConvertTarget,
                possibleNexts = predictiveSource.possibleNexts,
            ).orEmpty()
        if (stablePredictionCandidates.isEmpty()) {
            sessionState.stablePredictionCache = null
        }

        val bestThreePredictionCandidates: List<Candidate>
        if (options.requireJapanesePrediction.isEnabled && bestCandidateDataForPrediction != null) {
            val rawPredictions = getUniqueCandidate(
                AzooKeyPredictionHelper.getPredictionCandidate(
                    bestCandidateDataForPrediction = bestCandidateDataForPrediction,
                    composingText = inputData,
                    kana2Kanji = kana2Kanji,
                    useMemory = useMemory,
                ),
            ).sortedByDescending { it.value }.take(3)
            predictionResults = mergeStableCandidates(stablePredictionCandidates, rawPredictions, 3)
            bestThreePredictionCandidates = if (options.requireJapanesePrediction.shouldMix) {
                predictionResults
            } else {
                emptyList()
            }
        } else {
            sessionState.stablePredictionCache = null
            bestThreePredictionCandidates = emptyList()
        }

        val foreignCandidates = if (options.requireEnglishPrediction.isEnabled) {
            val englishCandidates = englishSpellChecker?.getForeignPredictionCandidates(inputData)
                ?: AzooKeyPredictionHelper.getForeignPredictionCandidates(inputData)
            englishPredictionResults = englishCandidates
            if (options.requireEnglishPrediction.shouldMix) englishCandidates else emptyList()
        } else {
            emptyList()
        }

        val topLevelAdditionalCandidates = getTopLevelAdditionalCandidate(inputData, options)
        val request = CandidateRequestBridge.toCandidateRequest(
            composingText = inputData,
            options = options,
            runtime = com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRuntimeContext(),
            mode = CandidateRequestMode.Normal,
        )

        val mixedCandidates = getUniqueCandidate(
            bestFiveSentenceCandidates +
                bestThreePredictionCandidates +
                foreignCandidates +
                topLevelAdditionalCandidates +
                userShortcutsCandidates,
        )
        val fullCandidates = if (options.requireJapanesePrediction.shouldMix) {
            mergeStableCandidates(stablePredictionCandidates, mixedCandidates, 5)
        } else {
            mixedCandidates.sortedByDescending { it.value }.take(5)
        }

        val uniqueFirstClauseCandidates = getUniqueCandidate(
            clauseResult.map { data ->
                val first = data.clauses.first()
                val count = first.first.dataEndIndex.coerceAtLeast(0)
                Candidate(
                    string = first.first.text,
                    type = CandidateType.NBEST,
                    length = first.first.reading.length.toUByte(),
                    score = first.second.toInt(),
                    value = first.second,
                    yomi = first.first.reading,
                    data = data.data.take(count + 1),
                    lastMid = first.first.mid,
                    rubyCount = first.first.reading.length,
                )
            },
        )

        var firstClauseResults = uniqueFirstClauseCandidates
            .sortedWith(compareByDescending<Candidate> { it.rubyCount }.thenByDescending { it.value })
            .take(5)

        val seenCandidate = fullCandidates.map { it.string }.toMutableSet()
        val firstClauseCandidates = getUniqueCandidate(uniqueFirstClauseCandidates, seenCandidate)
            .sortedWith(compareByDescending<Candidate> { it.rubyCount }.thenByDescending { it.value })
            .take(5)
        firstClauseCandidates.forEach { seenCandidate.add(it.string) }

        val dicCandidates = latticeResult.second[
            AzooKeyLatticeDualIndexMap.DualIndex.BothIndex(inputIdx = 0, surfaceIdx = 0),
        ].let { array ->
            (array.inputIndexedNodes + array.surfaceIndexedNodes).map { node ->
                val surfaceRange = node.range as AzooKeyLatticeRange.Surface
                Candidate(
                    string = node.entry.surface,
                    type = CandidateType.PART_OF_LETTERS,
                    length = (surfaceRange.to - surfaceRange.from).toUByte(),
                    score = node.entry.value.toInt(),
                    value = node.entry.value,
                    yomi = node.entry.reading,
                    data = listOf(node.entry),
                    lastMid = node.entry.mid,
                    rubyCount = node.entry.reading.length,
                )
            }
        }

        var wordCandidates = getUniqueCandidate(
            dicCandidates + getAdditionalCandidate(inputData, options),
            seenCandidate,
        ).sortedWith(compareByDescending<Candidate> { it.rubyCount }.thenByDescending { it.value })
        wordCandidates.forEach { seenCandidate.add(it.string) }

        val specialCandidates = getUniqueCandidate(
            options.specialCandidateProviders.flatMap { it.provide(request) },
            seenCandidate,
        )
        val wordList = wordCandidates.toMutableList()
        wordList.addAll(minOf(5, wordList.size), specialCandidates)

        sessionState.stablePredictionCache = StablePredictionCandidateCacheEntry(
            originalConvertTarget = inputData.convertTarget,
            suffixCount = predictiveSource.droppedSuffixCount,
            candidates = predictionResults,
        )

        var result = promoteExactReading(fullCandidates, bestFiveSentenceCandidates, wholeSentenceUniqueCandidates, inputData)
        result = result + firstClauseCandidates + wordList

        result = result.map { it.applyAppropriateActions().parseTemplate() }
        firstClauseResults = firstClauseResults.map { it.applyAppropriateActions().parseTemplate() }
        predictionResults = predictionResults.map { it.applyAppropriateActions().parseTemplate() }
        englishPredictionResults = englishPredictionResults.map { it.applyAppropriateActions().parseTemplate() }

        return AzooKeyStyleConversionResult(
            mainResults = result,
            predictionResults = predictionResults,
            englishPredictionResults = englishPredictionResults,
            firstClauseResults = firstClauseResults,
        )
    }

    private suspend fun buildUserShortcutsCandidates(
        inputData: ComposingText,
        kana2Kanji: AzooKeyKana2Kanji,
        searchUserTemplate: suspend (input: String, limit: Int) -> List<Candidate>,
        options: ConvertRequestOptions,
    ): List<Candidate> {
        val ruby = inputData.convertTarget.hiraganaToKatakana()
        if (ruby.isEmpty()) return emptyList()
        val loudsShortcuts = kana2Kanji.dicdataStore.getPerfectMatchedUserShortcuts(ruby).map { entry ->
            Candidate(
                string = entry.surface,
                type = CandidateType.USER_DICTIONARY,
                length = entry.surface.length.toUByte(),
                score = entry.value.toInt(),
                value = entry.value,
                yomi = entry.reading,
                data = listOf(entry),
                lastMid = entry.mid,
                rubyCount = entry.reading.length,
            )
        }
        val templateShortcuts = if (options.useUserTemplate) {
            searchUserTemplate(inputData.convertTarget, 8)
        } else {
            emptyList()
        }
        return getUniqueCandidate(loudsShortcuts + templateShortcuts)
    }

    private suspend fun getTopLevelAdditionalCandidate(
        inputData: ComposingText,
        options: ConvertRequestOptions,
    ): List<Candidate> {
        if (!options.englishCandidateInRoman2KanaInput) return emptyList()
        val isAsciiOnly = inputData.input.all { element ->
            when (val piece = element.piece) {
                is com.kazumaproject.markdownhelperkeyboard.converter.api.InputPiece.Character ->
                    piece.value.code in 32..126
                else -> false
            }
        }
        if (!isAsciiOnly) return emptyList()
        return englishSpellChecker?.getForeignPredictionCandidates(inputData, penalty = -10f)
            ?: AzooKeyPredictionHelper.getForeignPredictionCandidates(inputData, penalty = -10f)
    }

    private fun getKatakanaScore(katakana: String): Float {
        var score = 1f
        for (char in katakana) {
            score *= when (char) {
                in "プヴペィフ" -> 0.5f
                in "ュピポ" -> 0.6f
                in "パォグーム" -> 0.7f
                else -> 1f
            }
        }
        return score
    }

    private fun emptyResult() = AzooKeyConverterEngineResult(
        conversionResult = AzooKeyStyleConversionResult(mainResults = emptyList()),
        bunsetsuResult = null,
        usedAfterComplete = false,
        lattice = null,
    )

    private fun getUniqueCandidate(
        candidates: List<Candidate>,
        seenCandidates: Set<String> = emptySet(),
    ): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val textIndex = mutableMapOf<String, Int>()
        candidates.forEach { candidate ->
            if (candidate.string.isEmpty() || candidate.string in seenCandidates) return@forEach
            val existingIndex = textIndex[candidate.string]
            if (existingIndex != null) {
                val existing = result[existingIndex]
                if (existing.value < candidate.value || existing.effectiveRubyCount < candidate.effectiveRubyCount) {
                    result[existingIndex] = candidate
                }
            } else {
                textIndex[candidate.string] = result.size
                result.add(candidate)
            }
        }
        return result
    }

    private fun mergeStableCandidates(
        stableCandidates: List<Candidate>,
        otherCandidates: List<Candidate>,
        limit: Int,
    ): List<Candidate> {
        if (limit <= 0) return emptyList()
        val uniqueStable = getUniqueCandidate(stableCandidates)
        if (uniqueStable.size >= limit) return uniqueStable.take(limit)
        val seen = uniqueStable.map { it.string }.toSet()
        val additional = getUniqueCandidate(otherCandidates, seen)
            .sortedByDescending { it.value }
            .take(limit - uniqueStable.size)
        return uniqueStable + additional
    }

    private fun promoteExactReading(
        candidates: List<Candidate>,
        bestFive: List<Candidate>,
        wholeSentence: List<Candidate>,
        inputData: ComposingText,
    ): List<Candidate> {
        val target = inputData.convertTarget.hiraganaToKatakana()
        fun Candidate.matchesReading(): Boolean = data.joinToString("") { it.reading } == target

        val result = candidates.toMutableList()
        if (result.take(3).any { it.matchesReading() }) return result

        val index = result.drop(3).indexOfFirst { it.matchesReading() }
        if (index >= 0) {
            val candidate = result.removeAt(index + 3)
            result.add(minOf(2, result.size), candidate)
            return result
        }
        val fallback = bestFive.firstOrNull { it.matchesReading() }
            ?: wholeSentence.firstOrNull { it.matchesReading() }
        if (fallback != null) {
            result.add(minOf(2, result.size), fallback)
        }
        return result
    }

    private fun getAdditionalCandidate(inputData: ComposingText, options: ConvertRequestOptions): List<Candidate> {
        val string = inputData.convertTarget.hiraganaToKatakana()
        if (string.isEmpty()) return emptyList()
        val hiragana = inputData.convertTarget
        val entryBase = { surface: String, reading: String, value: Float ->
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry(
                surface = surface,
                reading = reading,
                leftId = AzooKeyCid.PROPER_NOUN,
                rightId = AzooKeyCid.PROPER_NOUN,
                mid = AzooKeyMid.GENERAL,
                wordCost = 14,
                value = value,
                sourceKind = AzooKeyDictionarySourceKind.System,
            )
        }
        val katakanaValue = -14f * getKatakanaScore(string)
        val candidates = mutableListOf(
            Candidate(
                string = string,
                type = CandidateType.KATAKANA,
                length = string.length.toUByte(),
                score = katakanaValue.toInt(),
                value = katakanaValue,
                yomi = string,
                data = listOf(entryBase(string, string, katakanaValue)),
                rubyCount = string.length,
            ),
            Candidate(
                string = hiragana,
                type = CandidateType.HIRAGANA,
                length = hiragana.length.toUByte(),
                score = -14,
                value = -14.5f,
                yomi = string,
                data = listOf(entryBase(hiragana, string, -14.5f)),
                rubyCount = hiragana.length,
            ),
            Candidate(
                string = string.uppercase(),
                type = CandidateType.NBEST,
                length = string.length.toUByte(),
                score = -14,
                value = -14.6f,
                yomi = string,
                data = listOf(entryBase(string.uppercase(), string, -14.6f)),
                rubyCount = string.length,
            ),
        )
        if (options.fullWidthRomanCandidate) {
            val fullWidth = string.toFullWidthRoman()
            candidates += Candidate(
                string = fullWidth,
                type = CandidateType.NBEST,
                length = fullWidth.length.toUByte(),
                score = -15,
                value = -14.7f,
                yomi = string,
                data = listOf(entryBase(fullWidth, string, -14.7f)),
                rubyCount = string.length,
            )
        }
        if (options.halfWidthKanaCandidate) {
            val halfWidth = string.toHalfWidthKana()
            candidates += Candidate(
                string = halfWidth,
                type = CandidateType.NBEST,
                length = halfWidth.length.toUByte(),
                score = -15,
                value = -15f,
                yomi = string,
                data = listOf(entryBase(halfWidth, string, -15f)),
                rubyCount = string.length,
            )
        }
        return candidates
    }

    private fun buildBunsetsuResult(candidates: List<Candidate>): BunsetsuCandidateResult? {
        if (candidates.isEmpty()) return null
        val splitPatternByCandidateString = mutableMapOf<String, List<Int>>()
        val splitPatterns = mutableListOf<List<Int>>()
        candidates.forEach { candidate ->
            val clauses = candidate.getClauses()
            val pattern = mutableListOf<Int>()
            var cumulative = 0
            for (i in 0 until clauses.size - 1) {
                cumulative += clauses[i].reading.length
                pattern.add(cumulative)
            }
            splitPatternByCandidateString[candidate.string] = pattern
            splitPatterns.add(pattern)
        }
        return BunsetsuCandidateResult(
            candidates = candidates,
            splitPatterns = splitPatterns.distinct(),
            splitPatternByCandidateString = splitPatternByCandidateString,
        )
    }
}

