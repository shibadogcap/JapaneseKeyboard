package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicyInput
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyRuntimeConversionPolicyResolver
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzConversionConfig
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImeZenzContextBuilderTest {
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun disablesZenzWhenHardwareKeyboardConnected() {
        val policy = publicPolicy()
        val context = ImeZenzContextBuilder.build(
            snapshot = null,
            policy = policy,
            config = ZenzConversionConfig(),
            leftContext = "前文",
            hasHardwareKeyboard = true,
            fallbackZenzEnabled = true,
            fallbackZenzRerankEnabled = true,
            fallbackNBest = 4,
        )
        assertFalse(context.zenzEnabled)
        assertFalse(context.asyncGenerationEnabled)
    }

    @Test
    fun snapshotOverridesFallbackZenzFlags() {
        val snapshot = baseSnapshot().copy(
            zenzEnableStatePreference = true,
            zenzRerankPreference = true,
            nBest = 8,
        )
        val context = ImeZenzContextBuilder.build(
            snapshot = snapshot,
            policy = publicPolicy(),
            config = ZenzConversionConfig(),
            leftContext = "",
            hasHardwareKeyboard = false,
            fallbackZenzEnabled = false,
            fallbackZenzRerankEnabled = false,
            fallbackNBest = 4,
        )
        assertTrue(context.zenzEnabled)
        assertTrue(context.rerankEnabled)
        assertEquals(8, context.nBest)
    }

    @Test
    fun publicPolicyEnablesZenzaiEvaluationAndAsyncGeneration() {
        val snapshot = baseSnapshot().copy(
            zenzEnableStatePreference = true,
            zenzRerankPreference = true,
        )
        val policy = AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = AzooKeyStyleLearningType.Nothing,
                zenzaiMode = AzooKeyStyleZenzaiMode.On,
                isComposing = true,
            ),
        )
        val context = ImeZenzContextBuilder.build(
            snapshot = snapshot,
            policy = policy,
            config = ZenzConversionConfig(),
            leftContext = "前文",
            hasHardwareKeyboard = false,
            fallbackZenzEnabled = false,
            fallbackZenzRerankEnabled = false,
            fallbackNBest = 4,
        )
        assertTrue(context.zenzEnabled)
        assertTrue(context.zenzaiEvaluationEnabled)
        assertTrue(context.asyncGenerationEnabled)
        assertEquals("前文", context.leftContext)
    }

    @Test
    fun privatePolicySuppressesZenzaiEvaluationEvenWithSnapshotRerank() {
        val snapshot = baseSnapshot().copy(
            zenzEnableStatePreference = true,
            zenzRerankPreference = true,
        )
        val policy = AzooKeyRuntimeConversionPolicyResolver.resolve(
            AzooKeyRuntimeConversionPolicyInput(
                learningType = AzooKeyStyleLearningType.Nothing,
                zenzaiMode = AzooKeyStyleZenzaiMode.On,
                isComposing = true,
                privacy = com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy(
                    isPrivateMode = true,
                ),
            ),
        )
        val context = ImeZenzContextBuilder.build(
            snapshot = snapshot,
            policy = policy,
            config = ZenzConversionConfig(),
            leftContext = "",
            hasHardwareKeyboard = false,
            fallbackZenzEnabled = true,
            fallbackZenzRerankEnabled = true,
            fallbackNBest = 4,
        )
        assertFalse(context.zenzaiEvaluationEnabled)
        assertFalse(context.asyncGenerationEnabled)
    }

    private fun baseSnapshot(): ImePreferencesSnapshot {
        return ImePreferencesSnapshot.from(
            appPreference = AppPreference,
            dictionarySourceResolver = null,
            customThemeCandidateItemPressedBgColorDefault = 0,
        )
    }

    private fun publicPolicy() = AzooKeyRuntimeConversionPolicyResolver.resolve(
        AzooKeyRuntimeConversionPolicyInput(
            learningType = AzooKeyStyleLearningType.Nothing,
            zenzaiMode = AzooKeyStyleZenzaiMode.On,
            isComposing = true,
        ),
    )
}