package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeCandidateRequestFactoryInputStyleTest {
    private fun basePreferences(
        keyboardLanguage: ConvertRequestOptions.KeyboardLanguage = ConvertRequestOptions.KeyboardLanguage.JaJp,
    ) = ImeCandidatePreferences(
        nBest = 10,
        useUserDictionary = false,
        useUserTemplate = false,
        useRomajiCandidates = false,
        useBunsetsu = false,
        useOmissionSearch = false,
        learningType = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType.Nothing,
        zenzaiMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode.Off,
        liveConversionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode.Disabled,
        privacy = com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy(),
        versionString = null,
        learnedPrefixMatchThreshold = 0,
        userDictionaryPrefixMatchThreshold = 0,
        isLearnDictionaryMode = false,
        romanize = { null },
        toHankakuAlphabet = { it },
        onNormalBunsetsuResult = { _, _ -> },
        isNgWordFilterEnabled = false,
        ngWords = emptyList(),
        ngWordPattern = Regex(""),
        isOrderOverrideEnabled = false,
        keyboardLanguage = keyboardLanguage,
    )

    @Test
    fun directInputEnablesBothPredictions() {
        val options = ImeCandidateRequestFactory.buildConvertRequestOptions(
            preferences = basePreferences(),
            mode = CandidateRequestMode.Normal,
            inputStyle = InputStyle.Direct,
        )
        assertEquals(AzooKeyStylePredictionMode.AutoMix, options.requireJapanesePrediction)
        assertEquals(AzooKeyStylePredictionMode.AutoMix, options.requireEnglishPrediction)
    }

    @Test
    fun roman2KanaJaKeyboardEnablesJapaneseOnly() {
        val options = ImeCandidateRequestFactory.buildConvertRequestOptions(
            preferences = basePreferences(keyboardLanguage = ConvertRequestOptions.KeyboardLanguage.JaJp),
            mode = CandidateRequestMode.Normal,
            inputStyle = InputStyle.Roman2Kana,
        )
        assertEquals(AzooKeyStylePredictionMode.AutoMix, options.requireJapanesePrediction)
        assertEquals(AzooKeyStylePredictionMode.Disabled, options.requireEnglishPrediction)
    }

    @Test
    fun roman2KanaEnUsKeyboardEnablesEnglishOnly() {
        val options = ImeCandidateRequestFactory.buildConvertRequestOptions(
            preferences = basePreferences(keyboardLanguage = ConvertRequestOptions.KeyboardLanguage.EnUs),
            mode = CandidateRequestMode.Normal,
            inputStyle = InputStyle.Roman2Kana,
        )
        assertEquals(AzooKeyStylePredictionMode.Disabled, options.requireJapanesePrediction)
        assertEquals(AzooKeyStylePredictionMode.AutoMix, options.requireEnglishPrediction)
    }

    @Test
    fun candidateSelectionDisablesPredictions() {
        val options = ImeCandidateRequestFactory.buildConvertRequestOptions(
            preferences = basePreferences().copy(isCandidateSelectionActive = true),
            mode = CandidateRequestMode.Normal,
            inputStyle = InputStyle.Direct,
        )
        assertEquals(AzooKeyStylePredictionMode.Disabled, options.requireJapanesePrediction)
        assertEquals(AzooKeyStylePredictionMode.Disabled, options.requireEnglishPrediction)
    }
}
