package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateAssemblerTest {
    @Test
    fun assembleReturnsSeparatedConversionResult() {
        val result = CandidateAssembler.assemble(
            request = baseRequest(),
            sources = CandidateSources(
                system = listOf(candidate("変換", CandidateType.NBEST, score = 10)),
                memory = listOf(candidate("学習", CandidateType.LEARNED_HISTORY, score = 1)),
                systemPrediction = listOf(candidate("予測", CandidateType.NBEST, score = 2)),
                firstClause = listOf(candidate("文節", CandidateType.PART_OF_LETTERS, score = 20))
            )
        )

        assertEquals(listOf("変換", "学習", "予測", "文節"), result.mainResults.map { it.string })
        assertEquals(listOf("予測"), result.predictionResults.map { it.string })
        assertEquals(listOf("文節"), result.firstClauseResults.map { it.string })
    }

    @Test
    fun assembleRespectsManualMixPredictionMode() {
        val result = CandidateAssembler.assemble(
            request = baseRequest(japanesePredictionMode = AzooKeyStylePredictionMode.ManualMix),
            sources = CandidateSources(
                system = listOf(candidate("変換", CandidateType.NBEST, score = 10)),
                systemPrediction = listOf(candidate("学習", CandidateType.LEARNED_HISTORY, score = 1)),
            )
        )

        assertEquals(listOf("変換"), result.mainResults.map { it.string })
        assertEquals(listOf("学習"), result.predictionResults.map { it.string })
    }

    @Test
    fun assembleTreatsNBestAsSearchWidthNotVisibleCandidateCap() {
        val result = CandidateAssembler.assemble(
            request = baseRequest(nBest = 1),
            sources = CandidateSources(
                system = listOf(
                    candidate("第一", CandidateType.NBEST, score = 10),
                    candidate("第二", CandidateType.NBEST, score = 11),
                ),
                special = listOf(candidate("♪", CandidateType.SYMBOL_SPECIAL, score = -30)),
                firstClause = listOf(candidate("文節", CandidateType.PART_OF_LETTERS, score = 20))
            )
        )

        assertEquals(listOf("第一", "第二", "文節", "♪"), result.mainResults.map { it.string })
        assertEquals(listOf("文節"), result.firstClauseResults.map { it.string })
    }

    private fun baseRequest(
        japanesePredictionMode: AzooKeyStylePredictionMode = AzooKeyStylePredictionMode.AutoMix,
        nBest: Int = 10,
    ): CandidateRequest {
        return CandidateRequest(
            input = "あずーきー",
            mode = CandidateRequestMode.Normal,
            nBest = nBest,
            useUserDictionary = true,
            useUserTemplate = true,
            useRomajiCandidates = true,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = japanesePredictionMode,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
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
            score = score
        )
    }
}
