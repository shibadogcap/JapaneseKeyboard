package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsPreferenceIndexTest {

    @Test
    fun load_indexesPreferencesWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = SettingsPreferenceIndex.load(context)
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.any { it.title.isNotBlank() })
    }
}
