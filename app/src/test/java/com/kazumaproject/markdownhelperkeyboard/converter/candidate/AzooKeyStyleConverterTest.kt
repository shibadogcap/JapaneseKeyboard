package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyStyleConverterTest {
    @Test
    fun convertInjectsSpecialCandidatesFromRequestProviders() {
        val result = AzooKeyStyleConverter.convert(
            request = baseRequest(input = "u3042", specialCandidateProviders = listOf(UnicodeSpecialCandidateProvider)),
            sources = CandidateSources(
                system = listOf(candidate("変換", CandidateType.NBEST, score = 10))
            )
        )

        assertEquals(listOf("変換", "あ"), result.mainResults.map { it.string })
    }

    @Test
    fun convertDoesNotUseDefaultProvidersWhenRequestOverridesProviders() {
        val result = AzooKeyStyleConverter.convert(
            request = baseRequest(input = "u3042", specialCandidateProviders = emptyList()),
            sources = CandidateSources(
                system = listOf(candidate("変換", CandidateType.NBEST, score = 10))
            )
        )

        assertEquals(listOf("変換"), result.mainResults.map { it.string })
    }

    @Test
    fun convertKeepsExistingSpecialSourcesBeforeGeneratedSpecialCandidates() {
        val result = AzooKeyStyleConverter.convert(
            request = baseRequest(input = "u3042", specialCandidateProviders = listOf(UnicodeSpecialCandidateProvider)),
            sources = CandidateSources(
                special = listOf(candidate("既存特殊", CandidateType.SPECIAL, score = -1))
            )
        )

        assertEquals(listOf("既存特殊", "あ"), result.mainResults.map { it.string })
    }

    @Test
    fun convertInjectsVersionSpecialCandidateProvider() {
        val result = AzooKeyStyleConverter.convert(
            request = baseRequest(
                input = "ばーじょん",
                specialCandidateProviders = listOf(VersionSpecialCandidateProvider),
                versionString = "JapaneseKeyboard Version 1.0",
            ),
            sources = CandidateSources(),
        )

        assertEquals(listOf("JapaneseKeyboard Version 1.0"), result.mainResults.map { it.string })
    }

    private fun baseRequest(
        input: String,
        specialCandidateProviders: List<SpecialCandidateProvider>,
        versionString: String? = null,
    ): CandidateRequest {
        return CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = 10,
            useUserDictionary = true,
            useUserTemplate = true,
            useRomajiCandidates = true,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
            specialCandidateProviders = specialCandidateProviders,
            versionString = versionString,
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
