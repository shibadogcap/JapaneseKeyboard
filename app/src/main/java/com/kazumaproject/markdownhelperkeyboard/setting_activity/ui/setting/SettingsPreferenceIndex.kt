package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.XmlRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.xmlpull.v1.XmlPullParser

object SettingsPreferenceIndex {

    data class Entry(
        val title: String,
        val summary: String?,
        val categoryTitle: String?,
        val tabIndex: Int,
        val tabTitle: String,
        val preferenceKey: String?,
        @IdRes val navigationActionId: Int? = null,
    ) {
        fun matches(query: String): Boolean {
            if (query.isBlank()) return true
            val normalized = query.trim().lowercase()
            return title.lowercase().contains(normalized) ||
                summary?.lowercase()?.contains(normalized) == true ||
                categoryTitle?.lowercase()?.contains(normalized) == true ||
                preferenceKey?.lowercase()?.contains(normalized) == true ||
                tabTitle.lowercase().contains(normalized)
        }
    }

    private data class TabSource(
        val tabIndex: Int,
        val tabTitle: String,
        @XmlRes val prefResId: Int?,
        val manualEntries: List<Entry> = emptyList(),
    )

    private val navigationByKey: Map<String, Int> = mapOf(
        "custom_romaji_preference" to R.id.action_navigation_setting_to_romajiMapFragment,
        "shortcut_toolbar_item_preference" to R.id.action_navigation_setting_to_shortcutSettingFragment,
        "candidate_tab_order_preference" to R.id.action_navigation_setting_to_candidateTabOrderFragment,
        "keyboard_selection_preference" to R.id.action_navigation_setting_to_keyboardSelectionFragment,
        "keyboard_key_letter_size_fragment_preference" to R.id.action_navigation_setting_to_keyCandidateLetterSizeFragment,
        "keyboard_screen_landscape_preference" to R.id.action_navigation_setting_to_keyboardSizeLandscapeFragment,
        "candidate_view_height_setting_fragment_preference" to R.id.action_navigation_setting_to_candidateViewHeightSettingFragment,
        "keyboard_screen_preference" to R.id.action_navigation_setting_to_keyboardSettingFragment,
        "candidate_view_height_landscape_setting_fragment_preference" to
            R.id.action_navigation_setting_to_candidateHeightLandscapeSettingFragment,
        "clipboard_history_preference_fragment" to R.id.action_navigation_setting_to_clipboardHistoryFragment,
        "delete_key_flick_left_targets_preference" to R.id.action_navigation_setting_to_deleteKeyFlickTargetsFragment,
        "cursor_move_after_commit_target_pairs_preference" to
            R.id.action_navigation_setting_to_cursorMoveTargetPairsFragment,
        "ng_word_preference" to R.id.action_navigation_setting_to_ngWordFragment,
        "system_user_dictionary_builder_preference" to R.id.action_navigation_setting_to_systemUserDictionaryBuilderFragment,
        "candidate_order_override_preference" to R.id.action_navigation_setting_to_candidateOrderOverrideFragment,
        "external_dictionary_settings_preference" to R.id.action_navigation_setting_to_externalDictionarySettingsFragment,
        "ngram_rule_preference" to R.id.action_navigation_setting_to_ngramRuleFragment,
        "learn_dictionary_view_preference" to R.id.navigation_learn_dictionary,
        "qwerty_button_size_preference" to R.id.action_navigation_setting_to_qwertyMarginSettingFragment,
        "qwerty_popup_view_style_preference" to R.id.action_navigation_setting_to_qwertyPopupStyleSettingFragment,
        "qwerty_number_key_flick_setting_preference" to
            R.id.action_navigation_setting_to_qwertyNumberKeyFlickSettingFragment,
        "kana_keyboard_letter_size_preference" to R.id.action_navigation_setting_to_tenKeyCandidateLetterSizeFragment,
        "tenkey_popup_view_style_preference" to R.id.action_navigation_setting_to_tenKeyPopupStyleSettingFragment,
        "sumire_keyboard_size_preference" to R.id.action_navigation_setting_to_flickKeyboardSizeSettingsFragment,
        "custom_keyboard_size_preference" to R.id.action_navigation_setting_to_flickKeyboardSizeSettingsFragment,
        "flick_keyboard_popup_view_style_preference" to
            R.id.action_navigation_setting_to_flickKeyboardPopupStyleListFragment,
        "circular_slot_action_setting_preference" to R.id.action_navigation_setting_to_circularSlotActionSettingFragment,
        "sumire_special_key_editor_preference" to R.id.action_navigation_setting_to_sumireSpecialKeyEditorFragment,
        "physical_keyboard_shortcut_setting_preference" to R.id.physicalKeyboardShortcutListFragment,
        "gemma_prompt_template_management_preference" to R.id.action_navigation_setting_to_gemmaPromptTemplateFragment,
    )

    fun load(context: Context): List<Entry> {
        val tabs = SettingTabRegistry.createTabs()
        val sources = buildTabSources(context, tabs)
        return sources.flatMap { source ->
            val xmlEntries = source.prefResId?.let { resId ->
                parsePreferenceXml(
                    context = context,
                    prefResId = resId,
                    tabIndex = source.tabIndex,
                    tabTitle = source.tabTitle,
                )
            }.orEmpty()
            source.manualEntries + xmlEntries
        }.distinctBy { "${it.tabIndex}:${it.preferenceKey ?: it.title}:${it.navigationActionId}" }
    }

    private fun buildTabSources(context: Context, tabs: List<SettingTabSpec>): List<TabSource> {
        val prefResources = buildList {
            add(R.xml.pref_common)
            add(null)
            add(R.xml.pref_custom)
            if (AppVariantConfig.hasZenz) add(R.xml.pref_zenz)
            if (AppVariantConfig.hasGemma) add(R.xml.pref_gemma)
            add(R.xml.pref_dictionary)
            add(R.xml.pref_kana)
            add(R.xml.pref_qwerty)
            add(R.xml.pref_sumire)
            add(R.xml.pref_tablet)
            add(R.xml.pref_hardware_keyboard)
        }
        return tabs.mapIndexed { index, spec ->
            val tabTitle = spec.title(context)
            TabSource(
                tabIndex = index,
                tabTitle = tabTitle,
                prefResId = prefResources.getOrNull(index),
                manualEntries = if (prefResources.getOrNull(index) == null) {
                    themeTabEntries(index, tabTitle, context)
                } else {
                    emptyList()
                },
            )
        }
    }

    private fun themeTabEntries(tabIndex: Int, tabTitle: String, context: Context): List<Entry> {
        return listOf(
            entry(
                context = context,
                titleRes = R.string.keyboardthemefragment,
                summaryRes = R.string.settings_theme_tab_summary,
                categoryTitle = tabTitle,
                tabIndex = tabIndex,
                tabTitle = tabTitle,
                preferenceKey = null,
            ),
            entry(
                context = context,
                titleRes = R.string.pref_round_corner_keyboard_title,
                summaryRes = null,
                categoryTitle = tabTitle,
                tabIndex = tabIndex,
                tabTitle = tabTitle,
                preferenceKey = "round_corner_keyboard_preference",
            ),
            entry(
                context = context,
                titleRes = R.string.liquid_glass_effect,
                summaryRes = null,
                categoryTitle = tabTitle,
                tabIndex = tabIndex,
                tabTitle = tabTitle,
                preferenceKey = "liquid_glass_preference",
            ),
        )
    }

    private fun entry(
        context: Context,
        titleRes: Int,
        summaryRes: Int?,
        categoryTitle: String,
        tabIndex: Int,
        tabTitle: String,
        preferenceKey: String?,
        @IdRes navigationActionId: Int? = navigationByKey[preferenceKey],
    ): Entry {
        return Entry(
            title = context.getString(titleRes),
            summary = summaryRes?.let(context::getString),
            categoryTitle = categoryTitle,
            tabIndex = tabIndex,
            tabTitle = tabTitle,
            preferenceKey = preferenceKey,
            navigationActionId = navigationActionId,
        )
    }

    private fun parsePreferenceXml(
        context: Context,
        @XmlRes prefResId: Int,
        tabIndex: Int,
        tabTitle: String,
    ): List<Entry> {
        val parser = context.resources.getXml(prefResId)
        val entries = mutableListOf<Entry>()
        var eventType = parser.eventType
        var currentCategory: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "PreferenceCategory" -> {
                        currentCategory = readTitle(parser, context)
                    }

                    "Preference", "SwitchPreferenceCompat", "ListPreference", "SeekBarPreference",
                    "EditTextPreference", "MultiSelectListPreference",
                    -> {
                        val key = parser.getAttributeValue(ANDROID_NS, "key")
                            ?: parser.getAttributeValue(APP_NS, "key")
                        val title = readTitle(parser, context)
                        if (title.isNotBlank()) {
                            val summary = readSummary(parser, context)
                            entries += Entry(
                                title = title,
                                summary = summary,
                                categoryTitle = currentCategory,
                                tabIndex = tabIndex,
                                tabTitle = tabTitle,
                                preferenceKey = key,
                                navigationActionId = key?.let { navigationByKey[it] },
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return entries
    }

    private fun readTitle(parser: XmlPullParser, context: Context): String {
        val titleAttr = parser.getAttributeValue(ANDROID_NS, "title")
            ?: parser.getAttributeValue(APP_NS, "title")
        return resolveStringReference(titleAttr, context)
    }

    private fun readSummary(parser: XmlPullParser, context: Context): String? {
        val summaryAttr = parser.getAttributeValue(ANDROID_NS, "summary")
            ?: parser.getAttributeValue(APP_NS, "summary")
            ?: parser.getAttributeValue(ANDROID_NS, "summaryOn")
            ?: parser.getAttributeValue(APP_NS, "summaryOn")
        return summaryAttr?.let { resolveStringReference(it, context).takeIf(String::isNotBlank) }
    }

    private fun resolveStringReference(value: String?, context: Context): String {
        if (value.isNullOrBlank()) return ""
        if (value.startsWith("@string/")) {
            val name = value.removePrefix("@string/")
            val resId = context.resources.getIdentifier(name, "string", context.packageName)
            if (resId != 0) {
                return context.getString(resId)
            }
        }
        return value
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"
}
