package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
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
    fun learningTypeIsOnlyOutputWhenPredictionLearnEnabled() {
        val snapshot = ImePreferencesSnapshot.from(
            appPreference = AppPreference,
            dictionarySourceResolver = null,
            customThemeCandidateItemPressedBgColorDefault = 0,
        ).copy(enablePredictionSearchLearnDictionaryPreference = true)

        assertEquals(
            AzooKeyStyleLearningType.OnlyOutput,
            ImeCandidatePreferencesBuilder.learningTypeFromSnapshot(snapshot),
        )
    }

    @Test
    fun learningTypeIsNothingWhenPredictionLearnDisabled() {
        val snapshot = ImePreferencesSnapshot.from(
            appPreference = AppPreference,
            dictionarySourceResolver = null,
            customThemeCandidateItemPressedBgColorDefault = 0,
        ).copy(enablePredictionSearchLearnDictionaryPreference = false)

        assertEquals(
            AzooKeyStyleLearningType.Nothing,
            ImeCandidatePreferencesBuilder.learningTypeFromSnapshot(snapshot),
        )
    }
}