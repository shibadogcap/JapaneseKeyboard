package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateRequestSearchWidthTest {
    @Test
    fun effectiveSearchNBestUsesUserPreferenceDirectly() {
        assertEquals(4, request(input = "あずき", nBest = 4).effectiveSearchNBest)
        assertEquals(10, request(input = "あずき", nBest = 10).effectiveSearchNBest)
        assertEquals(40, request(input = "きょうはいいてんきですねよろしく", nBest = 40).effectiveSearchNBest)
    }

    private fun request(
        input: String,
        nBest: Int,
    ): CandidateRequest {
        return CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = nBest,
            useUserDictionary = true,
            useUserTemplate = true,
            useRomajiCandidates = true,
            useBunsetsu = true,
            useOmissionSearch = false,
            japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
        )
    }
}
