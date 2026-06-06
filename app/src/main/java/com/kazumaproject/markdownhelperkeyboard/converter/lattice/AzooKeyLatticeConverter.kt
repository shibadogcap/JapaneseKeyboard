package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryConnectionIdResolver
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionarySearcher
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType

/**
 * LOUDS + memory から lattice を組み立て、n-best 候補を返す。
 */
class AzooKeyLatticeConverter(
    private val decoder: AzooKeyLatticeDecoder = AzooKeyLatticeDecoder(),
    private val typoSearchers: List<AzooKeyLoudsTypoSearcher> = emptyList(),
) {
    suspend fun convert(
        request: CandidateRequest,
        loudsLookups: List<AzooKeyLoudsDictionarySearcher>,
        searchMemory: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
        nBest: Int,
        incrementalState: AzooKeyLatticeIncrementalState? = null,
    ): AzooKeyLatticeConversionResult {
        if (request.input.isBlank() || nBest <= 0) {
            incrementalState?.clear()
            return AzooKeyLatticeConversionResult(emptyList(), emptyList())
        }
        val needTypo = request.typoCorrectionMode != AzooKeyStyleTypoCorrectionMode.Disabled
        val store = AzooKeyLoudsBackedDicdataStore(
            loudsLookups = loudsLookups,
            searchMemory = searchMemory,
            typoSearchers = typoSearchers,
            enableTypoCorrection = needTypo,
        )
        val normalized = request.input.hiraganaToKatakana()
        val nodes = buildNodes(
            store = store,
            request = request,
            normalized = normalized,
            incrementalState = incrementalState,
        )
        incrementalState?.let { state ->
            state.normalizedInput = normalized
            state.latticeNodes = nodes
        }
        val inputLength = request.input.hiraganaToKatakana().length
        val decoded = decoder.decode(
            inputLength = inputLength,
            nodes = nodes,
            nBest = nBest,
        )
        val resolver = AzooKeyDictionaryConnectionIdResolver()
        val exactSingles = buildList {
            loudsLookups.forEach { lookup ->
                addAll(lookup.exactEntries(request.input))
            }
            if (request.shouldReadMemoryDictionary) {
                addAll(searchMemory(request.input, nBest))
            }
        }
            .distinctBy { it.reading to it.surface }
            .map { entry ->
                entry.toCandidate(
                    type = CandidateType.NBEST,
                    connectionIdResolver = resolver,
                )
            }
        val main = (exactSingles + decoded)
            .groupBy { it.string }
            .map { (_, candidates) ->
                candidates.maxWith(
                    compareBy<Candidate> { it.score }
                        .thenBy { it.value }
                )
            }
            .sortedWith(
                compareByDescending<Candidate> { it.score }
                    .thenByDescending { it.value }
            )
            .take(nBest)
        return AzooKeyLatticeConversionResult(
            mainCandidates = main,
            latticeNodes = nodes,
        )
    }

    private suspend fun buildNodes(
        store: AzooKeyLoudsBackedDicdataStore,
        request: CandidateRequest,
        normalized: String,
        incrementalState: AzooKeyLatticeIncrementalState?,
    ): List<AzooKeyLatticeNode> {
        val useMemory = request.shouldReadMemoryDictionary
        val cachedInput = incrementalState?.normalizedInput
        val cachedNodes = incrementalState?.latticeNodes.orEmpty()
        if (
            incrementalState != null &&
            !cachedInput.isNullOrBlank() &&
            cachedNodes.isNotEmpty() &&
            normalized.startsWith(cachedInput) &&
            normalized.length > cachedInput.length
        ) {
            return store.buildLatticeNodesIncremental(
                input = request.input,
                useMemory = useMemory,
                previousNormalizedInput = cachedInput,
                previousNodes = cachedNodes,
            )
        }
        if (incrementalState != null && cachedInput != null && !normalized.startsWith(cachedInput)) {
            incrementalState.clear()
        }
        return store.buildLatticeNodes(
            input = request.input,
            useMemory = useMemory,
        )
    }
}

data class AzooKeyLatticeConversionResult(
    val mainCandidates: List<Candidate>,
    val latticeNodes: List<AzooKeyLatticeNode>,
)