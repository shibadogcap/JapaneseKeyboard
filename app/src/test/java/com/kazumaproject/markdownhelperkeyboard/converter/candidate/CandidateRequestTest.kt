package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRequestTest {
    @Test
    fun privateRequestDisablesPersonalizedConversionButKeepsExistingLearning() {
        val request = baseRequest(
            zenzaiMode = AzooKeyStyleZenzaiMode.On,
            experimentalZenzaiPredictiveInput = true,
            liveConversionMode = AzooKeyLiveConversionMode.Enabled,
            privacy = CandidateRequestPrivacy(isPrivateMode = true),
        )

        assertEquals(AzooKeyStylePredictionMode.AutoMix, request.effectiveJapanesePredictionMode)
        assertEquals(AzooKeyStyleLearningType.OnlyOutput, request.effectiveLearningType)
        assertEquals(AzooKeyStyleZenzaiMode.Off, request.effectiveZenzaiMode)
        assertTrue(request.shouldReadMemoryDictionary)
        assertTrue(request.shouldReadLearnedCandidates)
        assertFalse(request.shouldUseZenzaiPredictiveInput)
        assertTrue(request.shouldUseLiveConversion)
    }

    @Test
    fun suppressedSuggestionRequestDoesNotReadLearnedCandidates() {
        val request = baseRequest(
            privacy = CandidateRequestPrivacy(suppressSuggestions = true)
        )

        assertEquals(AzooKeyStylePredictionMode.Disabled, request.effectiveJapanesePredictionMode)
        assertFalse(request.shouldReadLearnedCandidates)
    }

    @Test
    fun publicRequestReadsMemoryDictionaryIndependentOfPrediction() {
        val request = baseRequest()

        assertEquals(AzooKeyStylePredictionMode.AutoMix, request.effectiveJapanesePredictionMode)
        assertEquals(AzooKeyStyleLearningType.OnlyOutput, request.effectiveLearningType)
        assertTrue(request.shouldReadMemoryDictionary)
        assertTrue(request.shouldReadLearnedCandidates)
    }

    @Test
    fun memoryDictionaryReadableWhenPredictionDisabled() {
        val request = baseRequest(
            japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
        )

        assertTrue(request.shouldReadMemoryDictionary)
        assertFalse(request.shouldReadLearnedCandidates)
    }

    @Test
    fun convertOptionsUseEffectivePrivacyValues() {
        val options = baseRequest(
            zenzaiMode = AzooKeyStyleZenzaiMode.On,
            privacy = CandidateRequestPrivacy(isPrivateMode = true),
        ).toAzooKeyStyleOptions()

        assertEquals(AzooKeyStylePredictionMode.AutoMix, options.japanesePredictionMode)
        assertEquals(AzooKeyStyleLearningType.OnlyOutput, options.learningType)
        assertEquals(AzooKeyStyleZenzaiMode.Off, options.zenzaiMode)
    }

    @Test
    fun publicRequestKeepsRequestedZenzaiAndLiveConversionPolicy() {
        val request = baseRequest(
            zenzaiMode = AzooKeyStyleZenzaiMode.On,
            experimentalZenzaiPredictiveInput = true,
            liveConversionMode = AzooKeyLiveConversionMode.Enabled,
        )

        assertEquals(AzooKeyStyleZenzaiMode.On, request.effectiveZenzaiMode)
        assertTrue(request.shouldUseZenzaiPredictiveInput)
        assertTrue(request.shouldUseLiveConversion)
    }

    @Test
    fun convertOptionsCarrySpecialCandidateProviders() {
        val customProviders = listOf(UnicodeSpecialCandidateProvider)
        val options = baseRequest(
            specialCandidateProviders = customProviders
        ).toAzooKeyStyleOptions()

        assertEquals(customProviders, options.specialCandidateProviders)
    }

    @Test
    fun convertingStateDisablesLiveConversion() {
        val request = baseRequest(
            liveConversionMode = AzooKeyLiveConversionMode.Enabled,
            isConverting = true,
        )

        assertFalse(request.shouldUseLiveConversion)
    }

    @Test
    fun candidateSelectionActiveDisablesLiveConversion() {
        val request = baseRequest(
            liveConversionMode = AzooKeyLiveConversionMode.Enabled,
            isCandidateSelectionActive = true,
        )

        assertFalse(request.shouldUseLiveConversion)
    }

    @Test
    fun convertOptionsCarryVersionStringMetadata() {
        val options = baseRequest(
            versionString = "JapaneseKeyboard Version 1.0",
        ).toAzooKeyStyleOptions()

        assertEquals("JapaneseKeyboard Version 1.0", options.versionString)
    }

    private fun baseRequest(
        privacy: CandidateRequestPrivacy = CandidateRequestPrivacy(),
        japanesePredictionMode: AzooKeyStylePredictionMode = AzooKeyStylePredictionMode.AutoMix,
        zenzaiMode: AzooKeyStyleZenzaiMode = AzooKeyStyleZenzaiMode.Off,
        experimentalZenzaiPredictiveInput: Boolean = false,
        liveConversionMode: AzooKeyLiveConversionMode = AzooKeyLiveConversionMode.Disabled,
        specialCandidateProviders: List<SpecialCandidateProvider> = DefaultSpecialCandidateProviders.providers,
        versionString: String? = null,
        isCandidateSelectionActive: Boolean = false,
        isConverting: Boolean = false,
        isDirectInputMode: Boolean = false,
    ): CandidateRequest {
        return CandidateRequest(
            input = "あずーきー",
            mode = CandidateRequestMode.Normal,
            nBest = 10,
            useUserDictionary = true,
            useUserTemplate = true,
            useRomajiCandidates = true,
            useBunsetsu = true,
            useOmissionSearch = true,
            japanesePredictionMode = japanesePredictionMode,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            zenzaiMode = zenzaiMode,
            experimentalZenzaiPredictiveInput = experimentalZenzaiPredictiveInput,
            liveConversionMode = liveConversionMode,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
            privacy = privacy,
            specialCandidateProviders = specialCandidateProviders,
            versionString = versionString,
            isCandidateSelectionActive = isCandidateSelectionActive,
            isConverting = isConverting,
            isDirectInputMode = isDirectInputMode,
        )
    }
}
