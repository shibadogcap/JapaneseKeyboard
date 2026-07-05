package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyInputTable
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputPiece
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import kotlin.math.exp
import kotlin.math.ln

/**
 * Swift [ZenzaiTypoCandidateGenerator](https://github.com/azooKey/AzooKeyKanaKanjiConverter) の Kotlin port。
 */
object AzooKeyZenzaiTypoCandidateGenerator {
    private enum class ObservedSource {
        ConvertTarget,
        ComposingInput,
    }

    private data class TypoGenerationConfig(
        val table: AzooKeyInputTable,
        val keyTopology: AzooKeyZenzaiTypoKeyTopology,
        val observedSource: ObservedSource,
    ) {
        val usesInputCharacterLMFilter: Boolean
            get() = observedSource == ObservedSource.ConvertTarget
    }

    private data class ObservedElement(
        val inputPiece: InputPiece,
        val character: Char,
    )

    private data class GeneratorState(
        val pending: String = "",
        val prevInputPiece: InputPiece? = null,
        val proxyLogp: Float = 0f,
    )

    private data class Hypothesis(
        val correctedInput: String,
        val emittedText: String,
        val emittedTokenIds: IntArray,
        val j: Int,
        val prevEmittedChar: Char?,
        val score: Float,
        val lmScore: Float,
        val channelCost: Float,
        val generatorState: GeneratorState?,
    )

    private data class ScoredHypothesis(val hypothesis: Hypothesis) : Comparable<ScoredHypothesis> {
        val score: Float = hypothesis.score
        override fun compareTo(other: ScoredHypothesis): Int = score.compareTo(other.score)
    }

    private data class DeferredRequest(
        val parent: Hypothesis,
        val baseState: GeneratorState,
        val correctedAppend: String,
        val observedCount: Int,
        val channelAdd: Float,
        val emitted: String,
        val pending: String,
        val lastInputPiece: InputPiece,
        val upperBoundScore: Float,
    )

    private data class TokenLogProb(val token: Int, val logProb: Float) : Comparable<TokenLogProb> {
        override fun compareTo(other: TokenLogProb): Int = logProb.compareTo(other.logProb)
    }

    private class FixedSizeHeap<T : Comparable<T>>(private val maxSize: Int) {
        private val heap = java.util.PriorityQueue<T>()
        val unordered: List<T> get() = heap.toList()
        val min: T? get() = heap.peek()

        fun insertIfPossible(item: T) {
            if (maxSize <= 0) return
            if (heap.size < maxSize) {
                heap.add(item)
            } else {
                val currentMin = heap.peek() ?: return
                if (item > currentMin) {
                    heap.poll()
                    heap.add(item)
                }
            }
        }
    }

    private class LmScorer(
        private val engine: ZenzEnginePort,
        private val cache: AzooKeyZenzaiTypoGenerationCache,
    ) {
        suspend fun encodeRaw(text: String): IntArray {
            cache.encodeCache[text]?.let { return it }
            val tokenIds = engine.typoEncodeRaw(text)
            cache.encodeCache[text] = tokenIds
            return tokenIds
        }

        suspend fun topKCharacters(emittedTokenIds: IntArray, k: Int): List<Char> {
            val nextLogProbs = nextLogProbs(emittedTokenIds) ?: return emptyList()
            val heap = FixedSizeHeap<TokenLogProb>(maxSize = maxOf(1, k * 4))
            nextLogProbs.forEachIndexed { tokenId, logProb ->
                heap.insertIfPossible(TokenLogProb(tokenId, logProb))
            }
            val chars = mutableListOf<Char>()
            val seen = mutableSetOf<Char>()
            for (item in heap.unordered.sortedDescending()) {
                val ch = tokenToSingleCharacter(item.token) ?: continue
                if (seen.add(ch)) {
                    chars.add(ch)
                    if (chars.size >= k) break
                }
            }
            return chars
        }

        suspend fun appendAndScore(
            emittedTokenIds: IntArray,
            lmScore: Float,
            appendText: String,
        ): Pair<IntArray, Float>? {
            val appendTokenIds = encodeRaw(appendText)
            if (appendTokenIds.isEmpty()) {
                return emittedTokenIds to lmScore
            }
            var currentTokenIds = emittedTokenIds
            var currentScore = lmScore
            for (tokenId in appendTokenIds) {
                val logProbs = nextLogProbs(currentTokenIds) ?: return null
                if (tokenId !in logProbs.indices) return null
                currentScore += logProbs[tokenId]
                currentTokenIds = currentTokenIds + tokenId
            }
            return currentTokenIds to currentScore
        }

        private fun tokenToSingleCharacter(token: Int): Char? {
            cache.tokenCharCache[token]?.let { return it }
            val ch = engine.typoTokenToSingleCharacter(token)
            cache.tokenCharCache[token] = ch
            return ch
        }

        suspend fun nextLogProbs(emittedTokenIds: IntArray): FloatArray? {
            val key = emittedTokenIds.toList()
            cache.nextLogProbCache[key]?.let { return it }
            val values = engine.typoNextLogProbs(cache.prompt, emittedTokenIds) ?: return null
            cache.nextLogProbCache[key] = values
            return values
        }
    }

    suspend fun generate(
        engine: ZenzEnginePort,
        leftSideContext: String,
        composingText: ComposingText,
        inputStyle: InputStyle,
        config: AzooKeyExperimentalTypoCorrectionConfig = AzooKeyExperimentalTypoCorrectionConfig(),
        cache: AzooKeyZenzaiTypoGenerationCache,
        customInputTable: AzooKeyInputTable? = null,
    ): List<AzooKeyZenzaiTypoCandidate> {
        val mode = resolveGenerationConfig(inputStyle, customInputTable)
        val observedElements = observedElements(composingText, mode.observedSource)
        if (observedElements.isEmpty()) return emptyList()

        val maxSteps = config.maxSteps ?: (observedElements.size * 2 + 8)
        val scorer = createScorer(engine, leftSideContext, cache)
        var beam = listOf(initialHypothesis())

        repeat(maxSteps) {
            val (expanded, allConsumed) = expandWithDeferred(
                beam = beam,
                observedElements = observedElements,
                table = mode.table,
                keyTopology = mode.keyTopology,
                useInputCharacterLMFilter = mode.usesInputCharacterLMFilter,
                scorer = scorer,
                config = config,
            )
            if (expanded.isEmpty()) return@repeat
            beam = expanded.sortedByDescending { it.score }.take(config.beamSize)
            if (allConsumed) return@repeat
        }

        val finals = run {
            val consumed = beam.filter { it.j == observedElements.size }
            if (consumed.isNotEmpty()) consumed
            else beam.mapNotNull { completeHypothesis(it, observedElements, mode.table, scorer) }
        }.toMutableList()

        completeHypothesis(initialHypothesis(), observedElements, mode.table, scorer)?.let { finals.add(it) }
        if (finals.isEmpty()) return emptyList()

        val sorted = finals.sortedByDescending { it.score }
        val bestScore = sorted.first().score
        val unique = linkedMapOf<String, AzooKeyZenzaiTypoCandidate>()
        for (hypothesis in sorted) {
            val convertedText = hypothesis.emittedText + (hypothesis.generatorState?.pending.orEmpty())
            val candidate = AzooKeyZenzaiTypoCandidate(
                correctedInput = hypothesis.correctedInput,
                convertedText = convertedText,
                score = hypothesis.score,
                lmScore = hypothesis.lmScore,
                channelCost = hypothesis.channelCost,
                prominence = exp(hypothesis.score - bestScore),
            )
            val existing = unique[candidate.correctedInput]
            if (existing != null && existing.score >= candidate.score) continue
            unique[candidate.correctedInput] = candidate
            if (unique.size >= config.nBest * 3) break
        }
        return unique.values.sortedByDescending { it.score }.take(config.nBest)
    }

    private suspend fun createScorer(
        engine: ZenzEnginePort,
        leftSideContext: String,
        cache: AzooKeyZenzaiTypoGenerationCache,
    ): LmScorer {
        val promptPrefix = typoCorrectionPromptPrefix(leftSideContext)
        val vocab = engine.vocabSize()
        if (cache.vocabSize != 0 && cache.vocabSize != vocab) {
            cache.invalidateAll()
        }
        cache.vocabSize = vocab
        if (cache.prompt != promptPrefix || cache.promptTokenIds.isEmpty()) {
            cache.prompt = promptPrefix
            cache.promptTokenIds = engine.typoEncodeRaw(promptPrefix)
            cache.nextLogProbCache.clear()
        }
        return LmScorer(engine, cache)
    }

    private fun initialHypothesis() = Hypothesis(
        correctedInput = "",
        emittedText = "",
        emittedTokenIds = IntArray(0),
        j = 0,
        prevEmittedChar = null,
        score = 0f,
        lmScore = 0f,
        channelCost = 0f,
        generatorState = GeneratorState(),
    )

    private fun resolveGenerationConfig(
        inputStyle: InputStyle,
        customInputTable: AzooKeyInputTable?,
    ): TypoGenerationConfig = when (inputStyle) {
        InputStyle.Roman2Kana -> TypoGenerationConfig(
            table = customInputTable ?: AzooKeyInputTable.Default,
            keyTopology = AzooKeyZenzaiTypoKeyTopology.MacOsStandardQwerty,
            observedSource = ObservedSource.ComposingInput,
        )
        else -> TypoGenerationConfig(
            table = AzooKeyInputTable.Empty,
            keyTopology = AzooKeyZenzaiTypoKeyTopology.IOsStandardFlickTenkey,
            observedSource = ObservedSource.ConvertTarget,
        )
    }

    private fun observedElements(
        composingText: ComposingText,
        source: ObservedSource,
    ): List<ObservedElement> = when (source) {
        ObservedSource.ConvertTarget -> composingText.convertTarget
            .hiraganaToKatakana()
            .map { ch ->
                ObservedElement(InputPiece.Character(ch), ch)
            }
        ObservedSource.ComposingInput -> composingText.input.mapNotNull { element ->
            canonicalCharacter(element.piece)?.let { ch ->
                ObservedElement(element.piece, ch)
            }
        }
    }

    private fun canonicalCharacter(piece: InputPiece): Char? = when (piece) {
        is InputPiece.Character -> piece.value.lowercaseChar()
        InputPiece.CompositionSeparator -> null
    }

    private suspend fun expandWithDeferred(
        beam: List<Hypothesis>,
        observedElements: List<ObservedElement>,
        table: AzooKeyInputTable,
        keyTopology: AzooKeyZenzaiTypoKeyTopology,
        useInputCharacterLMFilter: Boolean,
        scorer: LmScorer,
        config: AzooKeyExperimentalTypoCorrectionConfig,
    ): Pair<List<Hypothesis>, Boolean> {
        val heap = FixedSizeHeap<ScoredHypothesis>(maxSize = maxOf(1, config.beamSize))
        val deferredRequests = mutableListOf<DeferredRequest>()
        var allConsumed = true

        for (hypothesis in beam) {
            if (hypothesis.j >= observedElements.size) {
                heap.insertIfPossible(ScoredHypothesis(hypothesis))
                continue
            }
            allConsumed = false
            val (immediate, deferred) = expandCandidates(
                hypothesis = hypothesis,
                observedElements = observedElements,
                table = table,
                keyTopology = keyTopology,
                useInputCharacterLMFilter = useInputCharacterLMFilter,
                scorer = scorer,
                config = config,
            )
            immediate.forEach { heap.insertIfPossible(ScoredHypothesis(it)) }
            deferredRequests.addAll(deferred)
        }

        if (deferredRequests.isNotEmpty()) {
            deferredRequests.sortByDescending { it.upperBoundScore }
            for (request in deferredRequests) {
                if (heap.unordered.size >= maxOf(1, config.beamSize)) {
                    val cutoff = heap.min?.score ?: break
                    if (request.upperBoundScore < cutoff) break
                }
                val evaluated = evaluateAdvance(
                    parent = request.parent,
                    baseState = request.baseState,
                    correctedAppend = request.correctedAppend,
                    observedCount = request.observedCount,
                    channelAdd = request.channelAdd,
                    emitted = request.emitted,
                    pending = request.pending,
                    lastInputPiece = request.lastInputPiece,
                    table = table,
                    scorer = scorer,
                ) ?: continue
                heap.insertIfPossible(ScoredHypothesis(evaluated))
            }
        }

        val expanded = heap.unordered.sortedByDescending { it.score }.map { it.hypothesis }
        return expanded to allConsumed
    }

    private suspend fun expandCandidates(
        hypothesis: Hypothesis,
        observedElements: List<ObservedElement>,
        table: AzooKeyInputTable,
        keyTopology: AzooKeyZenzaiTypoKeyTopology,
        useInputCharacterLMFilter: Boolean,
        scorer: LmScorer,
        config: AzooKeyExperimentalTypoCorrectionConfig,
    ): Pair<List<Hypothesis>, List<DeferredRequest>> {
        if (hypothesis.j !in observedElements.indices) return listOf(hypothesis) to emptyList()
        val baseState = hypothesis.generatorState ?: return listOf(hypothesis) to emptyList()
        val observedElement = observedElements[hypothesis.j]
        val observed = observedElement.character
        val isInputTail = hypothesis.j == observedElements.lastIndex
        val neighborDistances = neighborDistances(observedElement.inputPiece, keyTopology)
        val allowed = buildSet {
            add(observed)
            addAll(neighborDistances.keys)
        }
        val targetChars = if (useInputCharacterLMFilter) {
            val lmTopChars = scorer.topKCharacters(hypothesis.emittedTokenIds, config.topK).toSet()
            val scoredTargets = allowed.intersect(lmTopChars + observed)
            if (scoredTargets.isEmpty()) listOf(observed) else scoredTargets.sorted()
        } else {
            allowed.sorted()
        }

        val immediate = mutableListOf<Hypothesis>()
        val deferred = mutableListOf<DeferredRequest>()

        suspend fun appendImmediate(
            correctedAppend: String,
            observedCount: Int,
            channelAdd: Float,
            emitted: String,
            pending: String,
            lastInputPiece: InputPiece,
        ) {
            evaluateAdvance(
                parent = hypothesis,
                baseState = baseState,
                correctedAppend = correctedAppend,
                observedCount = observedCount,
                channelAdd = channelAdd,
                emitted = emitted,
                pending = pending,
                lastInputPiece = lastInputPiece,
                table = table,
                scorer = scorer,
            )?.let { immediate.add(it) }
        }

        suspend fun evaluateOrDefer(
            correctedAppend: String,
            observedCount: Int,
            channelAdd: Float,
            emitted: String,
            pending: String,
            lastInputPiece: InputPiece,
        ) {
            if (emitted.isEmpty()) {
                appendImmediate(correctedAppend, observedCount, channelAdd, emitted, pending, lastInputPiece)
                return
            }
            val oldProxyLogp = baseState.proxyLogp
            if (!oldProxyLogp.isFinite()) return
            val baseLMScore = hypothesis.lmScore - oldProxyLogp
            val firstChar = emitted.first()
            val firstTokens = scorer.encodeRaw(firstChar.toString())
            if (firstTokens.size != 1) {
                appendImmediate(correctedAppend, observedCount, channelAdd, emitted, pending, lastInputPiece)
                return
            }
            val nextLogProbs = scorer.nextLogProbs(hypothesis.emittedTokenIds) ?: run {
                appendImmediate(correctedAppend, observedCount, channelAdd, emitted, pending, lastInputPiece)
                return
            }
            val firstToken = firstTokens[0]
            if (firstToken !in nextLogProbs.indices) return
            val upperBoundLM = baseLMScore + nextLogProbs[firstToken]
            val upperBoundScore = upperBoundLM - (hypothesis.channelCost + channelAdd)
            deferred.add(
                DeferredRequest(
                    parent = hypothesis,
                    baseState = baseState,
                    correctedAppend = correctedAppend,
                    observedCount = observedCount,
                    channelAdd = channelAdd,
                    emitted = emitted,
                    pending = pending,
                    lastInputPiece = lastInputPiece,
                    upperBoundScore = upperBoundScore,
                ),
            )
        }

        suspend fun addAdvance(
            trueSeq: List<Char>,
            observedCount: Int,
            channelAdd: Float,
            lastInputPiece: InputPiece,
        ) {
            val last = trueSeq.lastOrNull() ?: return
            val correctedAppend = trueSeq.joinToString("")
            var pending = baseState.pending
            val emittedBuilder = StringBuilder()
            for (char in trueSeq) {
                val consumed = consumeWithEmission(pending, char, table)
                emittedBuilder.append(consumed.emitted)
                pending = consumed.pending
            }
            val emitted = emittedBuilder.toString()
            val reachesTail = hypothesis.j + observedCount - 1 == observedElements.lastIndex
            if (reachesTail && pending.isNotEmpty()) {
                val observedLast = observedElements[hypothesis.j + observedCount - 1].character
                if (last != observedLast || channelAdd > 0f) return
            }
            evaluateOrDefer(correctedAppend, observedCount, channelAdd, emitted, pending, lastInputPiece)
        }

        for (target in targetChars) {
            val isIdentity = target == observed
            val substitutionDistance = neighborDistances[target] ?: 1.0f
            addAdvance(
                trueSeq = listOf(target),
                observedCount = 1,
                channelAdd = if (isIdentity) 0f else config.alpha * substitutionDistance,
                lastInputPiece = if (isIdentity) observedElement.inputPiece else InputPiece.Character(target),
            )
        }

        if (!isInputTail) {
            val prevInput = baseState.prevInputPiece
            if (prevInput != null) {
                val insertionDistance = neighborDistances(prevInput, keyTopology)[observed]
                if (insertionDistance != null) {
                    val newProxyLogp = pendingProxyLogProb(
                        pending = baseState.pending,
                        emittedTokenIds = hypothesis.emittedTokenIds,
                        table = table,
                        scorer = scorer,
                    )
                    if (newProxyLogp.isFinite()) {
                        val inserted = hypothesis.copy(
                            j = hypothesis.j + 1,
                            channelCost = hypothesis.channelCost + config.beta * insertionDistance,
                            lmScore = hypothesis.lmScore - baseState.proxyLogp + newProxyLogp,
                        ).let { it.copy(score = it.lmScore - it.channelCost) }
                        val insertedState = baseState.copy(proxyLogp = newProxyLogp)
                        immediate.add(inserted.copy(generatorState = insertedState))
                    }
                }
            }
        }

        if (hypothesis.j + 1 in observedElements.indices) {
            val observed2 = observedElements[hypothesis.j + 1].character
            if (observed != observed2) {
                addAdvance(
                    trueSeq = listOf(observed2, observed),
                    observedCount = 2,
                    channelAdd = config.gamma,
                    lastInputPiece = observedElement.inputPiece,
                )
            }
        }

        return immediate to deferred
    }

    private suspend fun evaluateAdvance(
        parent: Hypothesis,
        baseState: GeneratorState,
        correctedAppend: String,
        observedCount: Int,
        channelAdd: Float,
        emitted: String,
        pending: String,
        lastInputPiece: InputPiece,
        table: AzooKeyInputTable,
        scorer: LmScorer,
    ): Hypothesis? {
        val oldProxyLogp = baseState.proxyLogp
        if (!oldProxyLogp.isFinite()) return null
        val baseLMScore = parent.lmScore - oldProxyLogp
        var emittedTokenIds = parent.emittedTokenIds
        var emittedLogp = 0f
        var prevEmittedChar = parent.prevEmittedChar
        if (emitted.isNotEmpty()) {
            val appended = scorer.appendAndScore(emittedTokenIds, 0f, emitted) ?: return null
            emittedTokenIds = appended.first
            emittedLogp = appended.second
            prevEmittedChar = emitted.last()
        }
        val newProxyLogp = pendingProxyLogProb(pending, emittedTokenIds, table, scorer)
        if (!newProxyLogp.isFinite()) return null
        val nextState = baseState.copy(
            pending = pending,
            prevInputPiece = lastInputPiece,
            proxyLogp = newProxyLogp,
        )
        val lmScore = baseLMScore + emittedLogp + newProxyLogp
        val channelCost = parent.channelCost + channelAdd
        return parent.copy(
            correctedInput = parent.correctedInput + correctedAppend,
            emittedText = parent.emittedText + emitted,
            emittedTokenIds = emittedTokenIds,
            lmScore = lmScore,
            channelCost = channelCost,
            score = lmScore - channelCost,
            j = parent.j + observedCount,
            prevEmittedChar = prevEmittedChar,
            generatorState = nextState,
        )
    }

    private suspend fun completeHypothesis(
        hypothesis: Hypothesis,
        observedElements: List<ObservedElement>,
        table: AzooKeyInputTable,
        scorer: LmScorer,
    ): Hypothesis? {
        if (hypothesis.j >= observedElements.size) return hypothesis
        var state = hypothesis.generatorState ?: return null
        val oldProxyLogp = state.proxyLogp
        if (!oldProxyLogp.isFinite()) return null
        var completed = hypothesis
        val baseLMScore = completed.lmScore - oldProxyLogp
        val newlyEmitted = StringBuilder()
        while (completed.j < observedElements.size) {
            val observed = observedElements[completed.j].character
            val consumed = consumeWithEmission(state.pending, observed, table)
            state = state.copy(
                pending = consumed.pending,
                prevInputPiece = observedElements[completed.j].inputPiece,
            )
            completed = completed.copy(
                correctedInput = completed.correctedInput + observed,
                j = completed.j + 1,
            )
            newlyEmitted.append(consumed.emitted)
        }
        var emittedTokenIds = completed.emittedTokenIds
        var emittedLogp = 0f
        if (newlyEmitted.isNotEmpty()) {
            val appended = scorer.appendAndScore(emittedTokenIds, 0f, newlyEmitted.toString()) ?: return null
            emittedTokenIds = appended.first
            emittedLogp = appended.second
            completed = completed.copy(
                prevEmittedChar = newlyEmitted.last(),
                emittedText = completed.emittedText + newlyEmitted,
            )
        }
        val newProxyLogp = pendingProxyLogProb(state.pending, emittedTokenIds, table, scorer)
        if (!newProxyLogp.isFinite()) return null
        state = state.copy(proxyLogp = newProxyLogp)
        val lmScore = baseLMScore + emittedLogp + newProxyLogp
        return completed.copy(
            generatorState = state,
            emittedTokenIds = emittedTokenIds,
            lmScore = lmScore,
            score = lmScore - completed.channelCost,
        )
    }

    private data class EmissionResult(val emitted: String, val pending: String)

    private fun consumeWithEmission(
        pending: String,
        newChar: Char,
        table: AzooKeyInputTable,
    ): EmissionResult {
        val raw = pending + newChar
        val converted = applyInputTable(raw, table)
        val nextPending = pendingSuffix(raw, converted, table)
        if (nextPending.isEmpty()) {
            return EmissionResult(converted, "")
        }
        if (converted.length < nextPending.length) {
            return EmissionResult("", nextPending)
        }
        return EmissionResult(converted.dropLast(nextPending.length), nextPending)
    }

    private fun applyInputTable(raw: String, table: AzooKeyInputTable): String =
        table.convertRawToKatakana(raw)

    private fun pendingSuffix(raw: String, converted: String, table: AzooKeyInputTable): String {
        if (raw.isEmpty()) return ""
        val rawChars = raw.toList()
        for (length in rawChars.size downTo 1) {
            val suffix = rawChars.takeLast(length).joinToString("")
            if (!table.hasContinuation(suffix)) continue
            val suffixDisplay = applyInputTable(suffix, table)
            if (suffixDisplay == suffix && converted.endsWith(suffixDisplay)) {
                return suffix
            }
        }
        return ""
    }

    private suspend fun pendingProxyLogProb(
        pending: String,
        emittedTokenIds: IntArray,
        table: AzooKeyInputTable,
        scorer: LmScorer,
    ): Float {
        if (pending.isEmpty()) return 0f
        val firstTokenIds = pendingFirstTokenIds(pending, table, scorer)
        if (firstTokenIds.isEmpty()) return Float.NEGATIVE_INFINITY
        val nextLogProbs = scorer.nextLogProbs(emittedTokenIds) ?: return Float.NEGATIVE_INFINITY
        var maxLogProb = Float.NEGATIVE_INFINITY
        for (tokenId in firstTokenIds) {
            if (tokenId in nextLogProbs.indices) {
                maxLogProb = maxOf(maxLogProb, nextLogProbs[tokenId])
            }
        }
        if (!maxLogProb.isFinite()) return Float.NEGATIVE_INFINITY
        var sumExp = 0f
        for (tokenId in firstTokenIds) {
            if (tokenId in nextLogProbs.indices) {
                sumExp += exp(nextLogProbs[tokenId] - maxLogProb)
            }
        }
        if (sumExp <= 0f) return Float.NEGATIVE_INFINITY
        return maxLogProb + ln(sumExp)
    }

    private suspend fun pendingFirstTokenIds(
        pending: String,
        table: AzooKeyInputTable,
        scorer: LmScorer,
    ): List<Int> {
        val tokenIds = linkedSetOf<Int>()
        for (next in table.possibleNextDisplays(pending)) {
            val firstChar = next.firstOrNull() ?: continue
            val firstToken = scorer.encodeRaw(firstChar.toString())
            if (firstToken.size == 1) tokenIds.add(firstToken[0])
        }
        return tokenIds.sorted()
    }

    private fun neighborDistances(
        piece: InputPiece,
        topology: AzooKeyZenzaiTypoKeyTopology,
    ): Map<Char, Float> {
        val observed = canonicalCharacter(piece) ?: return emptyMap()
        return topology.neighborDistances(observed)
    }
}
