package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateRequestSearchWidthTest {
    @Test
    fun effectiveSearchNBestTreatsPreferenceAsSearchWidthFloorForShortInput() {
        assertEquals(24, request(input = "あずき", nBest = 4).effectiveSearchNBest)
    }

    @Test
    fun effectiveSearchNBestExpandsForLongComposingText() {
        assertEquals(48, request(input = "きょうはいいてん", nBest = 4).effectiveSearchNBest)
        assertEquals(64, request(input = "きょうはいいてんきですねよろしく", nBest = 4).effectiveSearchNBest)
    }

    @Test
    fun effectiveSearchNBestHonorsLargeUserPreferenceButCapsRunawaySearch() {
        assertEquals(72, request(input = "あずき", nBest = 72).effectiveSearchNBest)
        assertEquals(80, request(input = "きょうはいいてんきですねよろしく", nBest = 120).effectiveSearchNBest)
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
