package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateSourcesTest {
    @Test
    fun mainCandidatesPreserveNonPredictionSourceOrder() {
        val sources = CandidateSources(
            memory = listOf(candidate("学習", CandidateType.LEARNED_HISTORY)),
            userTemplate = listOf(candidate("テンプレート", 30.toByte())),
            userDictionary = listOf(candidate("ユーザー辞書", CandidateType.USER_DICTIONARY)),
            system = listOf(candidate("変換", CandidateType.NBEST)),
            romaji = listOf(candidate("romaji", CandidateType.ENGLISH))
        )

        assertEquals(
            listOf("テンプレート", "ユーザー辞書", "変換", "学習", "romaji"),
            sources.mainCandidates.map { it.string }
        )
    }

    @Test
    fun conversionResultUsesMemoryInMainLaneAndSystemPredictionSeparately() {
        val result = CandidateSources(
            memory = listOf(candidate("学習", CandidateType.LEARNED_HISTORY, score = 1, value = -1f)),
            systemPrediction = listOf(candidate("システム予測", CandidateType.NBEST, score = 2, value = -2f)),
            system = listOf(candidate("変換", CandidateType.NBEST, score = 10, value = -10f))
        ).toConversionResult(baseRequest())

        assertEquals(listOf("変換", "学習", "システム予測"), result.mainResults.map { it.string })
        assertEquals(listOf("システム予測"), result.predictionResults.map { it.string })
    }

    @Test
    fun privateRequestSuppressesMemoryAndPredictionLanes() {
        val result = CandidateSources(
            memory = listOf(candidate("学習", CandidateType.LEARNED_HISTORY, score = 1)),
            systemPrediction = listOf(candidate("システム予測", CandidateType.NBEST, score = 2)),
            system = listOf(candidate("変換", CandidateType.NBEST, score = 10))
        ).toConversionResult(
            baseRequest(privacy = CandidateRequestPrivacy(isPrivateMode = true))
        )

        assertEquals(listOf("変換"), result.mainResults.map { it.string })
        assertEquals(emptyList<String>(), result.predictionResults.map { it.string })
    }

    @Test
    fun conversionResultInjectsSpecialCandidatesThroughConverterFacade() {
        val result = CandidateSources(
            system = listOf(candidate("変換", CandidateType.NBEST, score = 10))
        ).toConversionResult(
            baseRequest(
                input = "u3042",
                specialCandidateProviders = listOf(UnicodeSpecialCandidateProvider)
            )
        )

        assertEquals(listOf("変換", "あ"), result.mainResults.map { it.string })
    }

    @Test
    fun fromSuggestionListsBuildsSourceBundleForCurrentImeServicePaths() {
        val sources = CandidateSources.fromSuggestionLists(
            learnedCandidates = listOf(candidate("学習", CandidateType.LEARNED_HISTORY)),
            userTemplateCandidates = listOf(candidate("テンプレート", 30.toByte())),
            userDictionaryCandidates = listOf(candidate("ユーザー辞書", CandidateType.USER_DICTIONARY)),
            engineCandidates = listOf(candidate("変換", CandidateType.NBEST)),
            romajiCandidates = listOf(candidate("romaji", CandidateType.ENGLISH)),
            specialCandidates = listOf(candidate("あ", CandidateType.UNICODE_SPECIAL))
        )

        assertEquals(listOf("学習"), sources.memory.map { it.string })
        assertEquals(listOf("あ"), sources.special.map { it.string })
        assertEquals(
            listOf("テンプレート", "ユーザー辞書", "変換", "学習", "romaji"),
            sources.mainCandidates.map { it.string }
        )
    }

    private fun baseRequest(
        input: String = "あずーきー",
        privacy: CandidateRequestPrivacy = CandidateRequestPrivacy(),
        specialCandidateProviders: List<SpecialCandidateProvider> = emptyList(),
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
            privacy = privacy,
            specialCandidateProviders = specialCandidateProviders,
        )
    }

    private fun candidate(
        string: String,
        type: Byte,
        score: Int = 100,
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
