package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyGraphRegisteredNode
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLattice
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyMutableLatticeNode
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort

data class AzooKeyZenzaiResult(
    val result: AzooKeyMutableLatticeNode,
    val lattice: AzooKeyLattice,
    val cache: AzooKeyZenzaiCache,
)

class AzooKeyZenzaiConverter(
    private val kana2Kanji: AzooKeyKana2Kanji,
    private val zenzEngine: ZenzEnginePort?,
    private val options: ConvertRequestOptions,
) {
    suspend fun allZenzai(
        inputData: ComposingText,
        options: ConvertRequestOptions,
        cache: AzooKeyZenzaiCache?,
        useMemory: Boolean,
        leftSideContext: String,
    ): AzooKeyZenzaiResult {
        val latticeInputData = kana2Kanji.zenzaiLatticeInputData(inputData)
        var constraint = cache?.getNewConstraint(latticeInputData) ?: AzooKeyPrefixConstraint()
        val eosNode = AzooKeyMutableLatticeNode.createResultNode()
        var lattice = AzooKeyLattice()
        var latticeInitialized = false
        val constructedCandidates = mutableListOf<Pair<AzooKeyGraphRegisteredNode, Candidate>>()
        val insertedCandidates = mutableListOf<Pair<AzooKeyGraphRegisteredNode, Candidate>>()
        var inferenceLimit = options.zenzaiInferenceLimit

        while (true) {
            val preprocessedLattice = when {
                latticeInitialized -> {
                    lattice.resetNodeStates()
                    lattice
                }
                else -> cache?.getPreprocessedLattice(
                    newInputData = latticeInputData,
                    kana2Kanji = kana2Kanji,
                    useMemory = useMemory,
                )
            }

            val draftResult = if (constraint.isEmpty) {
                kana2Kanji.kana2latticeAll(
                    inputData = latticeInputData,
                    nBest = 2,
                    needTypoCorrection = false,
                    useMemory = useMemory,
                    preprocessedLattice = preprocessedLattice,
                )
            } else {
                kana2Kanji.kana2latticeAllWithPrefixConstraint(
                    inputData = latticeInputData,
                    nBest = 3,
                    constraint = constraint,
                    preprocessedLattice = preprocessedLattice,
                    useMemory = useMemory,
                )
            }

            if (!latticeInitialized) {
                lattice = draftResult.second
                latticeInitialized = true
            }

            val candidates = kana2Kanji.getCandidateDataFromResult(draftResult.first)
                .map { kana2Kanji.processClauseCandidate(it) }
            constructedCandidates += draftResult.first.prevs.zip(candidates)

            val best = candidates.withIndex().maxByOrNull { it.value.value }
            if (best == null) {
                eosNode.prevs.clear()
                eosNode.prevs.addAll(insertedCandidates.map { it.first })
                return AzooKeyZenzaiResult(
                    result = eosNode,
                    lattice = lattice,
                    cache = AzooKeyZenzaiCache(
                        inputData = latticeInputData,
                        prefixConstraint = AzooKeyPrefixConstraint(),
                        satisfyingCandidate = null,
                        lattice = lattice,
                    ),
                )
            }

            var index = best.index
            var candidate = best.value

            reviewLoop@ while (true) {
                insertedCandidates.add(0, draftResult.first.prevs[index] to candidate)

                if (inferenceLimit == 0) {
                    eosNode.prevs.clear()
                    eosNode.prevs.addAll(insertedCandidates.map { it.first })
                    return AzooKeyZenzaiResult(
                        result = eosNode,
                        lattice = lattice,
                        cache = AzooKeyZenzaiCache(
                            inputData = latticeInputData,
                            prefixConstraint = constraint,
                            satisfyingCandidate = candidate,
                            lattice = lattice,
                        ),
                    )
                }

                val reviewResult = if (zenzEngine != null && options.zenzProfile.isNotBlank()) {
                    AzooKeyZenzaiCandidateEvaluator.evaluate(
                        zenzEngine = zenzEngine,
                        options = options,
                        leftContext = leftSideContext,
                        inputData = inputData,
                        candidate = candidate,
                        prefixConstraint = constraint,
                        requestRichCandidates = options.requestRichCandidates,
                    )
                } else {
                    ZenzaiCandidateEvaluationResult.Pass(score = 0f)
                }
                inferenceLimit -= 1

                val (nextAction, updatedConstraint) = AzooKeyZenzaiReview.review(
                    kana2Kanji = kana2Kanji,
                    candidateIndex = index,
                    candidates = candidates,
                    reviewResult = reviewResult,
                    constraint = constraint,
                )
                constraint = updatedConstraint

                when (nextAction) {
                    is AzooKeyZenzaiNextAction.ContinueLoop -> break@reviewLoop
                    is AzooKeyZenzaiNextAction.Retry -> {
                        index = nextAction.candidateIndex
                        candidate = candidates[index]
                    }
                    is AzooKeyZenzaiNextAction.ReturnResult -> {
                        if (options.requestRichCandidates) {
                            insertRichAlternatives(
                                alternativeConstraints = nextAction.alternativeConstraints,
                                constructedCandidates = constructedCandidates,
                                insertedCandidates = insertedCandidates,
                                latticeInputData = latticeInputData,
                                lattice = lattice,
                                useMemory = useMemory,
                            )
                        }
                        eosNode.prevs.clear()
                        eosNode.prevs.addAll(insertedCandidates.map { it.first })
                        return AzooKeyZenzaiResult(
                            result = eosNode,
                            lattice = lattice,
                            cache = AzooKeyZenzaiCache(
                                inputData = latticeInputData,
                                prefixConstraint = nextAction.constraint,
                                satisfyingCandidate = if (nextAction.satisfied) candidate else null,
                                lattice = lattice,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun insertRichAlternatives(
        alternativeConstraints: List<ZenzaiCandidateEvaluationResult.AlternativeConstraint>,
        constructedCandidates: List<Pair<AzooKeyGraphRegisteredNode, Candidate>>,
        insertedCandidates: MutableList<Pair<AzooKeyGraphRegisteredNode, Candidate>>,
        latticeInputData: ComposingText,
        lattice: AzooKeyLattice,
        useMemory: Boolean,
    ) {
        for (alternativeConstraint in alternativeConstraints.asReversed()) {
            if (alternativeConstraint.probabilityRatio <= 0.25f) continue
            val normalized = AzooKeyPrefixConstraint.normalized(
                constraintBytes = alternativeConstraint.prefix.toByteArray(Charsets.UTF_8),
                defaultHasEos = false,
                ignoreMemoryAndUserDictionary = false,
            )
            val mostLikely = constructedCandidates
                .filter { ( _, cand) -> kana2Kanji.candidateSatisfies(cand, normalized) }
                .maxByOrNull { it.second.value }
            if (mostLikely != null) {
                insertedCandidates.add(1, mostLikely)
                continue
            }
            if (alternativeConstraint.probabilityRatio <= 0.5f) continue
            lattice.resetNodeStates()
            val draftResult = kana2Kanji.kana2latticeAllWithPrefixConstraint(
                inputData = latticeInputData,
                nBest = 3,
                constraint = normalized,
                preprocessedLattice = lattice,
                useMemory = useMemory,
            )
            val candidates = kana2Kanji.getCandidateDataFromResult(draftResult.first)
                .map { kana2Kanji.processClauseCandidate(it) }
            val best = candidates.withIndex().maxByOrNull { it.value.value } ?: continue
            insertedCandidates.add(1, draftResult.first.prevs[best.index] to best.value)
        }
    }
}
