package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.extensions.katakanaToHiragana
import com.kazumaproject.markdownhelperkeyboard.converter.api.CandidateRequestBridge
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.api.deleteBackwardFromCursor
import com.kazumaproject.markdownhelperkeyboard.converter.api.insertDirectAtCursor
import com.kazumaproject.markdownhelperkeyboard.converter.api.insertRoman2KanaAtCursor
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyConversionDefaults
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConversionResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyTypoCorrectionPolicy
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
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyExperimentalTypoCorrectionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidateGenerator
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoGenerationCache
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzModelIdentity
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.toZenzPromptContext
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
        zenzEngine: ZenzEnginePort? = null,
    ) : this(azooKeyDicdataFacadeSourceForTests(registry, connectionStore), zenzEngine, null)

    private data class SessionState(
        var previousInputData: ComposingText? = null,
        var lattice: AzooKeyLattice = AzooKeyLattice(),
        var completedData: Candidate? = null,
        var zenzaiCache: AzooKeyZenzaiCache? = null,
        var stablePredictionCache: StablePredictionCandidateCacheEntry? = null,
        var predictiveInputCache: PredictiveInputCacheEntry? = null,
        var zenzaiTypoCache: AzooKeyZenzaiTypoGenerationCache = AzooKeyZenzaiTypoGenerationCache(),
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
        val needTypo = !options.zenzaiMode.isEnabled &&
            AzooKeyTypoCorrectionPolicy.isClassicTypoCorrectionEnabled(options.typoCorrectionMode)
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
            session = session,
            searchMemory = searchMemory,
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

    data class PredictNextInputTextResult(
        val predictedText: String,
        val suffixCount: Int,
    )

    /** AzooKey [KanaKanjiConverter.predictNextInputText](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
    suspend fun predictNextInputText(
        leftSideContext: String,
        composingText: ComposingText,
        count: Int,
        options: ConvertRequestOptions,
        session: ConversionSession,
        inputStyle: InputStyle = composingText.input.lastOrNull()?.inputStyle ?: InputStyle.Direct,
        minLength: Int = 1,
        maxEntropy: Float? = null,
    ): PredictNextInputTextResult {
        // Android では zenzaiMode.On を v3 相当として扱う（Swift versionDependentMode.v3 ガード）。
        if (!options.zenzaiMode.isEnabled) {
            invalidatePredictiveInputCache(session.sessionId)
            return PredictNextInputTextResult(predictedText = "", suffixCount = 0)
        }
        val engine = zenzEngine ?: run {
            invalidatePredictiveInputCache(session.sessionId)
            return PredictNextInputTextResult(predictedText = "", suffixCount = 0)
        }
        val modelIdentity = options.zenzModelIdentity.ifBlank { ZenzModelIdentity.currentModelPath }
        val trimmedLeft = leftSideContext.takeLast(options.maxLeftSideContextLength)
        val cacheContext = PredictiveInputCacheContext(
            leftSideContext = trimmedLeft,
            inputStyle = inputStyle,
            zenzaiMode = options.zenzaiMode,
            zenzProfile = options.zenzProfile,
            zenzTopic = options.zenzTopic,
            zenzStyle = options.zenzStyle,
            zenzPreference = options.zenzPreference,
            zenzRightSideContext = options.zenzRightSideContext,
            zenzModelIdentity = modelIdentity,
        )
        cachedPredictiveInputText(
            sessionId = session.sessionId,
            context = cacheContext,
            composingText = composingText,
            count = count,
        )?.let { cached ->
            return PredictNextInputTextResult(predictedText = cached, suffixCount = 0)
        }
        val source = AzooKeyPredictiveInputResolver.resolve(composingText, options.roman2KanaTransducer)
        val predictedText = engine.predictNextInputText(
            prompt = options.toZenzPromptContext(trimmedLeft),
            composingText = source.baseConvertTarget,
            count = count,
            minLength = minLength,
            maxEntropy = maxEntropy,
            possibleNexts = source.possibleNexts,
        )
        val sessionState = sessions.getOrPut(session.sessionId) { SessionState() }
        if (predictedText.isEmpty()) {
            sessionState.predictiveInputCache = null
        } else {
            sessionState.predictiveInputCache = PredictiveInputCacheEntry(
                context = cacheContext,
                originalConvertTarget = composingText.convertTarget,
                suffixCount = source.droppedSuffixCount,
                predictedText = predictedText,
            )
        }
        return PredictNextInputTextResult(
            predictedText = predictedText,
            suffixCount = source.droppedSuffixCount,
        )
    }

    private fun cachedPredictiveInputText(
        sessionId: String,
        context: PredictiveInputCacheContext,
        composingText: ComposingText,
        count: Int,
    ): String? {
        val cache = sessions[sessionId]?.predictiveInputCache ?: return null
        if (cache.context != context) {
            invalidatePredictiveInputCache(sessionId)
            return null
        }
        val remaining = cache.remainingPrediction(composingText.convertTarget, count)
        if (remaining == null) {
            invalidatePredictiveInputCache(sessionId)
            return null
        }
        return remaining
    }

    private fun invalidatePredictiveInputCache(sessionId: String) {
        sessions[sessionId]?.predictiveInputCache = null
    }

    /** Swift [KanaKanjiConverter.experimentalRequestTypoCorrection](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。 */
    suspend fun experimentalRequestTypoCorrection(
        leftSideContext: String,
        composingText: ComposingText,
        options: ConvertRequestOptions,
        inputStyle: InputStyle,
        config: AzooKeyExperimentalTypoCorrectionConfig = AzooKeyExperimentalTypoCorrectionConfig(),
        session: ConversionSession,
    ): List<AzooKeyZenzaiTypoCandidate> {
        if (!options.zenzaiMode.isEnabled) return emptyList()
        val engine = zenzEngine ?: return emptyList()
        val sessionState = sessions.getOrPut(session.sessionId) { SessionState() }
        val modelIdentity = options.zenzModelIdentity.ifBlank { ZenzModelIdentity.currentModelPath }
        if (sessionState.zenzaiTypoCache.modelIdentity != modelIdentity) {
            sessionState.zenzaiTypoCache.invalidateForModelChange(modelIdentity)
        }
        return AzooKeyZenzaiTypoCandidateGenerator.generate(
            engine = engine,
            leftSideContext = leftSideContext,
            composingText = composingText,
            inputStyle = inputStyle,
            config = config,
            cache = sessionState.zenzaiTypoCache,
            customInputTable = options.roman2KanaTransducer.table,
        )
    }

    private suspend fun experimentalZenzaiPredictionCandidates(
        composingText: ComposingText,
        options: ConvertRequestOptions,
        session: ConversionSession,
        searchMemory: suspend (reading: String, limit: Int) -> List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry>,
        searchUserTemplate: suspend (input: String, limit: Int) -> List<Candidate>,
    ): List<Candidate> {
        if (!options.zenzaiMode.isEnabled || !options.experimentalZenzaiPredictiveInput) {
            return emptyList()
        }
        val inputStyle = composingText.input.lastOrNull()?.inputStyle ?: InputStyle.Direct
        val leftSideContext = options.zenzLeftSideContext.ifBlank {
            session.leftSideContext
        }.takeLast(options.maxLeftSideContextLength)
        val prediction = predictNextInputText(
            leftSideContext = leftSideContext,
            composingText = composingText,
            count = 10,
            minLength = 1,
            maxEntropy = 3.0f,
            options = options,
            session = session,
            inputStyle = inputStyle,
        )
        if (prediction.predictedText.isEmpty()) return emptyList()

        val insertText = if (inputStyle == InputStyle.Roman2Kana) {
            prediction.predictedText.katakanaToHiragana()
        } else {
            prediction.predictedText
        }
        var predictedComposingText = composingText
        if (prediction.suffixCount > 0) {
            predictedComposingText = predictedComposingText.deleteBackwardFromCursor(
                count = prediction.suffixCount,
                roman2Kana = options.roman2KanaTransducer,
            )
        }
        predictedComposingText = when (inputStyle) {
            InputStyle.Roman2Kana -> predictedComposingText.insertRoman2KanaAtCursor(
                insertText,
                options.roman2KanaTransducer,
            )
            else -> predictedComposingText.insertDirectAtCursor(insertText)
        }

        val fallbackOptions = options.copy(
            requireJapanesePrediction = AzooKeyStylePredictionMode.Disabled,
            requireEnglishPrediction = AzooKeyStylePredictionMode.Disabled,
        )
        val scratchSessionId = "${session.sessionId}-zenz-predictive-${System.nanoTime()}"
        val scratchSession = session.copy(sessionId = scratchSessionId)
        return try {
            requestCandidates(
                inputData = predictedComposingText,
                options = fallbackOptions,
                session = scratchSession,
                searchMemory = searchMemory,
                searchUserTemplate = searchUserTemplate,
            ).conversionResult.mainResults.firstOrNull()?.let { listOf(it) }.orEmpty()
        } finally {
            sessions.remove(scratchSessionId)
        }
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
                options = options,
            )
            val result = zenzai.allZenzai(
                inputData = inputData,
                options = options,
                cache = sessionState.zenzaiCache,
                useMemory = useMemory,
                leftSideContext = options.zenzLeftSideContext.ifBlank {
                    session.leftSideContext
                }.takeLast(options.maxLeftSideContextLength),
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
        session: ConversionSession,
        searchMemory: suspend (reading: String, limit: Int) -> List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry>,
        searchUserTemplate: suspend (input: String, limit: Int) -> List<Candidate>,
    ): AzooKeyStyleConversionResult {
        sessionState.lattice = latticeResult.second
        val useMemory = options.learningType != AzooKeyStyleLearningType.Nothing

        val clauseResult = kana2Kanji.getCandidateDataFromResult(latticeResult.first)
        if (clauseResult.isEmpty()) {
            sessionState.stablePredictionCache = null
            sessionState.predictiveInputCache = null
            val additional = getAdditionalCandidate(inputData, options)
            val request = CandidateRequestBridge.toCandidateRequest(
                composingText = inputData,
                options = options,
                runtime = com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRuntimeContext(),
                mode = CandidateRequestMode.Normal,
            )
            val seenCandidate = additional.map { it.string }.toMutableSet()
            val specialCandidates = getUniqueCandidate(
                options.specialCandidateProviders.flatMap { it.provide(request) },
                seenCandidate,
            )
            val mainResults = (additional + specialCandidates)
                .map { it.applyAppropriateActions().parseTemplate() }
            return AzooKeyStyleConversionResult(mainResults = mainResults, firstClauseResults = mainResults)
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
            sessionState.predictiveInputCache = null
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
            sessionState.predictiveInputCache = null
        }

        val bestThreePredictionCandidates: List<Candidate>
        if (options.requireJapanesePrediction.isEnabled && bestCandidateDataForPrediction != null) {
            val rawPredictions = getUniqueCandidate(
                AzooKeyPredictionHelper.getPredictionCandidate(
                    bestCandidateDataForPrediction = bestCandidateDataForPrediction,
                    composingText = inputData,
                    kana2Kanji = kana2Kanji,
                    useMemory = useMemory,
                    roman2Kana = options.roman2KanaTransducer,
                    experimentalFallback = {
                        experimentalZenzaiPredictionCandidates(
                            composingText = inputData,
                            options = options,
                            session = session,
                            searchMemory = searchMemory,
                            searchUserTemplate = searchUserTemplate,
                        )
                    },
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
            sessionState.predictiveInputCache = null
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

        val richCandidateList = options.requestRichCandidates
        val firstClauseLimit = if (richCandidateList) 10 else 5

        var firstClauseResults = uniqueFirstClauseCandidates
            .sortedWith(compareByDescending<Candidate> { it.rubyCount }.thenByDescending { it.value })
            .take(firstClauseLimit)
            .filter { AzooKeyJapaneseConversionText.isValidCandidateSurface(it.string) }

        val seenCandidate = fullCandidates.map { it.string }.toMutableSet()
        val firstClauseCandidates = getUniqueCandidate(uniqueFirstClauseCandidates, seenCandidate)
            .sortedWith(compareByDescending<Candidate> { it.rubyCount }.thenByDescending { it.value })
            .take(firstClauseLimit)
            .filter { AzooKeyJapaneseConversionText.isValidCandidateSurface(it.string) }
        firstClauseCandidates.forEach { seenCandidate.add(it.string) }

        val dicCandidates = latticeResult.second[
            AzooKeyLatticeDualIndexMap.DualIndex.BothIndex(inputIdx = 0, surfaceIdx = 0),
        ].let { array ->
            (array.inputIndexedNodes + array.surfaceIndexedNodes).mapNotNull { node ->
                if (!AzooKeyJapaneseConversionText.isValidCandidateSurface(node.entry.surface)) {
                    return@mapNotNull null
                }
                val length = when (val range = node.range) {
                    is AzooKeyLatticeRange.Surface -> (range.to - range.from).toUByte()
                    is AzooKeyLatticeRange.Input -> node.entry.reading.length.toUByte()
                }
                Candidate(
                    string = node.entry.surface,
                    type = candidateTypeForDictionarySource(node.entry.sourceKind),
                    length = length,
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

        if (richCandidateList && inputData.convertTarget.length > 1) {
            val multiChar = wordCandidates.filter { it.rubyCount > 1 }
            val singleChar = wordCandidates.filter { it.rubyCount <= 1 }.take(8)
            wordCandidates = multiChar + singleChar
        }
        wordCandidates.forEach { seenCandidate.add(it.string) }

        val specialCandidates = getUniqueCandidate(
            options.specialCandidateProviders.flatMap { it.provide(request) },
            seenCandidate,
        )
        val wordList = wordCandidates.toMutableList()
        wordList.addAll(minOf(5, wordList.size), specialCandidates)

        sessionState.stablePredictionCache = if (predictionResults.isNotEmpty()) {
            StablePredictionCandidateCacheEntry(
                originalConvertTarget = inputData.convertTarget,
                suffixCount = predictiveSource.droppedSuffixCount,
                candidates = predictionResults,
            )
        } else {
            null
        }

        var result = promoteExactReading(fullCandidates, bestFiveSentenceCandidates, wholeSentenceUniqueCandidates, inputData)
        result = result.filter { AzooKeyJapaneseConversionText.isValidCandidateSurface(it.string) }
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

    private fun candidateTypeForDictionarySource(sourceKind: AzooKeyDictionarySourceKind): Byte {
        return when (sourceKind) {
            AzooKeyDictionarySourceKind.Emoji -> CandidateType.EMOJI_LEGACY
            AzooKeyDictionarySourceKind.Symbol -> CandidateType.SYMBOL_LEGACY
            else -> CandidateType.PART_OF_LETTERS
        }
    }
}

