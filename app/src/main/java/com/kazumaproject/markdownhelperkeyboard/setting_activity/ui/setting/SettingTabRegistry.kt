package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.fragment.app.Fragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig

data class SettingTabSpec(
    val title: (Context) -> String,
    val fragmentClass: Class<out Fragment>,
)

object SettingTabRegistry {
    fun createTabs(): List<SettingTabSpec> {
        val tabs = mutableListOf(
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_common) },
                fragmentClass = CommonPreferenceFragment::class.java,
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.keyboardthemefragment) },
                fragmentClass = KeyboardThemeFragment::class.java,
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_custom_keyboard_title) },
                fragmentClass = CustomKeyboardPreferenceFragment::class.java,
            ),
        )

        if (AppVariantConfig.hasZenz) {
            tabs += SettingTabSpec(
                title = { "zenz" },
                fragmentClass = ZenzPreferenceFragment::class.java,
            )
        }

        if (AppVariantConfig.hasGemma) {
            tabs += SettingTabSpec(
                title = { "Gemma" },
                fragmentClass = GemmaPreferenceFragment::class.java,
            )
        }

        tabs += listOf(
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_dictionary) },
                fragmentClass = DictionaryPreferenceFragment::class.java,
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_kana) },
                fragmentClass = KanaPreferenceFragment::class.java,
            ),
            SettingTabSpec(
                title = { "QWERTY" },
                fragmentClass = QwertyPreferenceFragment::class.java,
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.category_sumire_input_keyboard_title) },
                fragmentClass = SumirePreferenceFragment::class.java,
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.tablet_preference_category_title) },
                fragmentClass = TabletPreferenceFragment::class.java,
            ),
            SettingTabSpec(
                title = { context -> context.getString(R.string.hardware_keyboard_category_title) },
                fragmentClass = HardwareKeyboardPreferenceFragment::class.java,
            ),
        )

        return tabs
    }
}
