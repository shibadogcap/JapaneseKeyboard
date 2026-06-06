package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzRerankFusionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzaiCandidateEvaluationResult
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHiraganaWithSymbols
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZenzConversionService @Inject constructor(
    private val zenzEngine: ZenzEnginePort,
) {
    fun shouldGenerate(request: ZenzGenerationRequest, policy: AzooKeyRuntimeConversionPolicy): Boolean {
        if (!policy.allowsPersonalizedConversion) return false
        if (request.insertReading.length <= 1) return false
        return request.insertReading.isAllHiraganaWithSymbols()
    }

    suspend fun generatePredictive(
        request: ZenzGenerationRequest,
        policy: AzooKeyRuntimeConversionPolicy,
    ): List<ZenzCandidate> {
        if (!shouldGenerate(request, policy)) {
            return emptyList()
        }
        val generated = zenzEngine.generateWithContext(
            profile = request.config.profile,
            leftContext = request.leftContext,
            inputKatakana = request.insertReading.hiraganaToKatakana(),
            maxTokens = request.config.maxTokens,
        )
        if (generated.isBlank()) return emptyList()
        return listOf(
            ZenzCandidate(
                string = generated,
                type = CandidateType.ZENZ,
                length = request.insertReading.length.toUByte(),
                score = 2000,
                originalString = request.insertReading,
            )
        )
    }

    suspend fun evaluateZenzai(
        request: ZenzPredictiveRequest,
    ): List<ZenzCandidate> {
        if (request.insertReading.length <= 1 || !request.insertReading.isAllHiraganaWithSymbols()) {
            return emptyList()
        }
        val firstCandidate = request.topDictionaryCandidate
        val raw = zenzEngine.candidateEvaluate(
            profile = request.config.profile,
            leftContext = request.leftContext,
            inputKatakana = request.insertReading.hiraganaToKatakana(),
            candidate = firstCandidate,
        )
        val parsed = ZenzaiCandidateEvaluationResult.parse(raw)
        return when (parsed) {
            ZenzaiCandidateEvaluationResult.Error -> listOf(
                zenzCandidate(request, firstCandidate, CandidateType.ZENZ),
            )
            is ZenzaiCandidateEvaluationResult.Pass -> {
                if (parsed.alternativeConstraints.isEmpty()) {
                    listOf(zenzCandidate(request, firstCandidate, CandidateType.ZENZ))
                } else {
                    val resolved = ZenzaiAlternativeConstraintPolicy.resolveBestCandidate(
                        request = request,
                        constraints = parsed.alternativeConstraints,
                        generateWithPrefixContext = { prefix ->
                            zenzEngine.generateWithContext(
                                profile = request.config.profile,
                                leftContext = prefix,
                                inputKatakana = request.insertReading.hiraganaToKatakana(),
                                maxTokens = request.config.maxTokens,
                            )
                        },
                        toZenzCandidate = { surface, type ->
                            zenzCandidate(request, surface, type)
                        },
                    )
                    listOfNotNull(resolved ?: zenzCandidate(request, firstCandidate, CandidateType.ZENZ))
                }
            }
            is ZenzaiCandidateEvaluationResult.WholeResult -> listOf(
                zenzCandidate(request, parsed.result, CandidateType.ZENZ),
            )
            is ZenzaiCandidateEvaluationResult.FixRequired -> {
                val prefix = parsed.prefix
                val fromPrefix = request.dictionaryCandidates
                    .take(request.nBest)
                    .firstOrNull { it.startsWith(prefix) }
                    ?: firstCandidate
                val engineCandidate = zenzCandidate(
                    request,
                    fromPrefix,
                    CandidateType.ZENZ_CONTEXTUAL,
                )
                val generated = zenzEngine.generateWithContext(
                    profile = request.config.profile,
                    leftContext = prefix,
                    inputKatakana = request.insertReading.hiraganaToKatakana(),
                    maxTokens = request.config.maxTokens,
                )
                val neuralCandidate = zenzCandidate(
                    request,
                    generated,
                    CandidateType.ZENZ_SPECIAL,
                )
                listOf(neuralCandidate, engineCandidate).maxByOrNull {
                    it.rank(prefix)
                }?.let { listOf(it) } ?: listOf(neuralCandidate)
            }
        }
    }

    suspend fun rerank(
        request: ZenzRerankRequest,
        policy: AzooKeyRuntimeConversionPolicy,
    ): List<Candidate>? {
        if (!policy.allowsPersonalizedConversion) return null
        if (!request.config.rerankEnabled) return null
        if (policy.shouldUseZenzai) return null
        if (request.config.hasHardwareKeyboard) return null
        if (request.insertReading.length <= 1 || !request.insertReading.isAllHiraganaWithSymbols()) {
            return null
        }
        if (request.candidates.size < 2) return null

        val targets = request.candidates.withIndex()
            .filter { it.value.length.toInt() == request.insertReading.length }
            .take(request.config.rerankTopK)
        if (targets.size < 2) return null

        val rawScores = zenzEngine.scoreCandidates(
            profile = request.config.profile,
            leftContext = request.leftContext,
            inputKatakana = request.insertReading.hiraganaToKatakana(),
            candidates = targets.map { it.value.string },
        )
        if (rawScores.size != targets.size) return null

        val rerankedTargets = ZenzRerankFusionPolicy.rerank(
            candidates = targets.map { it.value },
            rawZenzScores = rawScores.toList(),
            baseWeight = request.config.rerankBaseWeight,
            zenzWeight = request.config.rerankZenzWeight,
        ) ?: return null

        val merged = request.candidates.toMutableList()
        targets.indices.forEach { slot ->
            merged[targets[slot].index] = rerankedTargets[slot]
        }
        return merged
    }

    fun shouldRerank(request: CandidateRequest, config: ZenzConversionConfig): Boolean {
        return config.rerankEnabled &&
            request.allowsPersonalizedConversion &&
            !request.runtimeConversionPolicy.shouldUseZenzai &&
            !config.hasHardwareKeyboard
    }

    private fun zenzCandidate(
        request: ZenzPredictiveRequest,
        surface: String,
        type: Byte,
    ): ZenzCandidate {
        return ZenzCandidate(
            string = surface,
            type = type,
            length = request.insertReading.length.toUByte(),
            score = 2000,
            originalString = request.insertReading,
        )
    }

}