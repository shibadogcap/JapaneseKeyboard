package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyStyleCandidateServiceTest {
    @Test
    fun serviceMergesAuxiliaryAndSystemSourcesAndReturnsBunsetsuResult() = runTest {
        val bunsetsuResult = BunsetsuCandidateResult(
            candidates = listOf(candidate("変換", CandidateType.NBEST, score = 10, value = -10f)),
            splitPatterns = listOf(listOf(2)),
            splitPatternByCandidateString = mapOf("変換" to listOf(2))
        )
        val service = AzooKeyStyleCandidateService(
            auxiliarySourceProvider = SuspendCandidateSourceProvider {
                CandidateSources(
                    memory = listOf(candidate("学習", CandidateType.LEARNED_HISTORY, score = 5, value = -1f)),
                    userDictionary = listOf(candidate("辞書", CandidateType.USER_DICTIONARY, score = 6, value = -6f)),
                )
            },
            systemSourceProvider = SystemKanaKanjiCandidateSourceProvider(
                convertNormal = { SystemCandidateSourceResult(bunsetsuResult.candidates, bunsetsuResult) },
                convertOriginal = { SystemCandidateSourceResult(emptyList()) },
                convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
            )
        )

        val result = service.convert(baseRequest())

        assertEquals(listOf("学習"), result.sources.memory.map { it.string })
        assertEquals(listOf("辞書", "変換", "学習"), result.sources.mainCandidates.map { it.string })
        assertEquals(listOf("辞書", "変換", "学習"), result.conversionResult.mainResults.map { it.string })
        assertEquals(listOf(2), result.bunsetsuResult?.primarySplitPositions)
    }

    @Test
    fun serviceLetsConverterInjectSpecialCandidates() = runTest {
        val service = AzooKeyStyleCandidateService(
            auxiliarySourceProvider = SuspendCandidateSourceProvider { CandidateSources() },
            systemSourceProvider = SystemKanaKanjiCandidateSourceProvider(
                convertNormal = { SystemCandidateSourceResult(emptyList()) },
                convertOriginal = { SystemCandidateSourceResult(emptyList()) },
                convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
            )
        )

        val result = service.convert(
            baseRequest(
                input = "u3042",
                specialCandidateProviders = listOf(UnicodeSpecialCandidateProvider),
            )
        )

        assertEquals(listOf("あ"), result.conversionResult.mainResults.map { it.string })
    }

    private fun baseRequest(
        input: String = "あずーきー",
        specialCandidateProviders: List<SpecialCandidateProvider> = emptyList(),
    ): CandidateRequest {
        return CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = 10,
            useUserDictionary = true,
            useUserTemplate = true,
            useRomajiCandidates = true,
            useBunsetsu = true,
            useOmissionSearch = false,
            japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
            specialCandidateProviders = specialCandidateProviders,
        )
    }

    private fun candidate(
        string: String,
        type: Byte,
        score: Int,
        value: AzooKeyPValue = score.toFloat(),
    ): Candidate {
        return Candidate(
            string = string,
            type = type,
            length = string.length.toUByte(),
            score = score,
            value = value,
        )
    }
}
