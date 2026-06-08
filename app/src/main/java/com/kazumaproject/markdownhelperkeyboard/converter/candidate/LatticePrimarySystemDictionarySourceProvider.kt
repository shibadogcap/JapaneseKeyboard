package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeConverter
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeIncrementalState
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLoudsBackedDicdataStore

/**
 * LOUDS + memory を lattice 上で競合させ、本家 [convertToLattice](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当の n-best を返す。
 */
class LatticePrimarySystemDictionarySourceProvider(
    private val loudsLookups: List<AzooKeyLoudsDictionarySearcher>,
    private val searchMemory: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
    private val engine: EngineSystemDictionarySourceProvider,
    private val latticeConverter: AzooKeyLatticeConverter = AzooKeyLatticeConverter(),
    private val latticeIncrementalState: AzooKeyLatticeIncrementalState? = null,
    private val nBest: Int = 40,
) : SystemDictionarySourceProvider {
    override val lastBunsetsuResult: BunsetsuCandidateResult?
        get() = engine.lastBunsetsuResult

    override suspend fun provide(request: CandidateRequest): CandidateSources {
        if (request.useBunsetsu && request.mode != CandidateRequestMode.EnglishKana) {
            engine.warmBunsetsuMetadataOnly(request)
        }
        val result = latticeConverter.convert(
            request = request,
            loudsLookups = loudsLookups,
            searchMemory = searchMemory,
            nBest = request.effectiveSearchNBest.coerceAtLeast(nBest),
            incrementalState = latticeIncrementalState,
        )
        val prediction = if (request.effectiveJapanesePredictionMode.isEnabled) {
            loudsLookups.flatMap { lookup ->
                lookup.prefixEntries(
                    reading = request.input,
                    maxDepth = AzooKeyLoudsBackedDicdataStore.DEFAULT_MAX_PREFIX_DEPTH,
                    maxCount = request.effectiveSearchNBest.coerceAtLeast(nBest),
                )
            }
                .distinctBy { it.reading to it.surface }
                .sortedByDescending { it.value }
                .take(request.effectiveSearchNBest.coerceAtLeast(nBest))
                .map { entry ->
                    entry.toCandidate(
                        type = CandidateType.NBEST,
                        connectionIdResolver = AzooKeyDictionaryConnectionIdResolver(),
                    )
                }
        } else {
            emptyList()
        }
        val resolver = AzooKeyDictionaryConnectionIdResolver()
        val latticeWordCandidates = result.latticeNodes
            .asSequence()
            .filter { node -> node.startIndex == 0 && node.endIndex <= request.input.length }
            .map { node ->
                node.entry.toCandidate(
                    type = CandidateType.PART_OF_LETTERS,
                    connectionIdResolver = resolver,
                )
            }
            .toList()

        val rawPrefixCandidates = (1..request.input.length).flatMap { len ->
            val prefixYomi = request.input.substring(0, len)
            val hira = Candidate(
                string = prefixYomi,
                type = CandidateType.HIRAGANA,
                length = len.toUByte(),
                score = -100 - len,
                value = -100f - len,
                yomi = prefixYomi,
                leftId = 0,
                rightId = 0,
                isLearningTarget = true
            )
            val kata = Candidate(
                string = prefixYomi.hiraganaToKatakana(),
                type = CandidateType.KATAKANA,
                length = len.toUByte(),
                score = -105 - len,
                value = -105f - len,
                yomi = prefixYomi,
                leftId = 0,
                rightId = 0,
                isLearningTarget = true
            )
            listOf(hira, kata)
        }

        val wordCandidates = (latticeWordCandidates + rawPrefixCandidates)
            .distinctBy { candidate -> candidate.yomi to candidate.string }
            .sortedWith(
                compareByDescending<Candidate> { it.length.toInt() }
                    .thenByDescending { it.value }
            )
            .take(500)
            .toList()
        val firstClauseCandidates = wordCandidates
            .filter { candidate -> candidate.yomi != request.input || candidate.string !in result.mainCandidates.map { it.string } }
            .take(FIRST_CLAUSE_LIMIT)
        return CandidateSources(
            system = result.mainCandidates,
            systemPrediction = prediction,
            firstClause = firstClauseCandidates,
            word = wordCandidates,
        )
    }

    private companion object {
        const val FIRST_CLAUSE_LIMIT = 5
    }
}
