package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyConversionDefaults
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImeCandidatePreferencesBuilderTest {
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun nBestMatchesAzooKeyDefault() {
        val snapshot = baseSnapshot()
        val prefs = ImeCandidatePreferencesBuilder.build(
            snapshot = snapshot,
            runtime = ImeCandidateRuntimeSession(
                isPrivateMode = false,
                suppressSuggestions = false,
                isCandidateSelectionActive = false,
                isConverting = false,
                isDirectInputMode = false,
                qwertyMode = TenKeyQWERTYMode.Default,
                currentQwertyRomajiMode = false,
            ),
            appPreference = AppPreference,
            ngWords = emptyList(),
            ngWordPattern = Regex(""),
            romanize = { null },
            toHankakuAlphabet = { it },
            zenzaiEnabled = false,
        )
        assertEquals(AzooKeyConversionDefaults.N_BEST, prefs.nBest)
    }

    @Test
    fun learningTypeIsInputAndOutputByDefault() {
        val snapshot = baseSnapshot().copy(
            isLearnDictionaryMode = true,
            learningTypePreference = "input_and_output",
        )
        assertEquals(
            AzooKeyStyleLearningType.InputAndOutput,
            ImeCandidatePreferencesBuilder.learningTypeFromSnapshot(snapshot),
        )
    }

    @Test
    fun learningTypeIsOnlyOutputWhenConfigured() {
        val snapshot = baseSnapshot().copy(
            isLearnDictionaryMode = true,
            learningTypePreference = "only_output",
        )
        assertEquals(
            AzooKeyStyleLearningType.OnlyOutput,
            ImeCandidatePreferencesBuilder.learningTypeFromSnapshot(snapshot),
        )
    }

    @Test
    fun learningTypeIsNothingWhenLearnDictionaryDisabled() {
        val snapshot = baseSnapshot().copy(
            isLearnDictionaryMode = false,
            learningTypePreference = "input_and_output",
        )
        assertEquals(
            AzooKeyStyleLearningType.Nothing,
            ImeCandidatePreferencesBuilder.learningTypeFromSnapshot(snapshot),
        )
    }

    private fun baseSnapshot(): ImePreferencesSnapshot {
        return ImePreferencesSnapshot.from(
            appPreference = AppPreference,
            dictionarySourceResolver = null,
            customThemeCandidateItemPressedBgColorDefault = 0,
        )
    }
}
