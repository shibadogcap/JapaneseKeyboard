package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SeekBarWithEditTextPreference
import com.afollestad.materialdialogs.color.colorChooser
import com.google.android.material.color.DynamicColors
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class KeyboardThemeFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var appPreference: AppPreference

    private val selectEnterLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it, "custom_enter_icon", "enter") }
    }
    private val selectSpaceLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it, "custom_space_icon", "space") }
    }
    private val selectLeftLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it, "custom_left_arrow_icon", "left") }
    }
    private val selectRightLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it, "custom_right_arrow_icon", "right") }
    }
    private val selectModeSwitchLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it, "custom_mode_switch_icon", "mode_switch") }
    }
    private val selectUndoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it, "custom_undo_icon", "undo") }
    }
    private val selectEmojiLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it, "custom_emoji_icon", "emoji") }
    }
    private val selectDeleteLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it, "custom_delete_icon", "delete") }
    }
    private val selectFontKeyLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFontSelected(it, "custom_font_key_select", "custom_font_key") }
    }
    private val selectFontCandidateLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFontSelected(it, "custom_font_candidate_select", "custom_font_candidate") }
    }

    companion object {
        // System Theme Keys
        private const val PREF_KEY_DEFAULT = "theme_default"
        private const val PREF_KEY_ROUND_CORNER = "round_corner_keyboard_preference"

        // Liquid Glass Keys
        private const val PREF_KEY_LIQUID_GLASS = "liquid_glass_preference"
        private const val PREF_KEY_LIQUID_GLASS_BLUR = "liquid_glass_blur_preference"
        private const val PREF_KEY_LIQUID_GLASS_KEY_ALPHA = "liquid_glass_key_alpha_preference"

        // Custom Theme Keys
        private const val PREF_KEY_CUSTOM = "theme_custom"
        private const val PREF_KEY_CUSTOM_BG = "theme_custom_bg_color"
        private const val PREF_KEY_CUSTOM_KEY = "theme_custom_key_color"
        private const val PREF_KEY_CUSTOM_SPECIAL_KEY = "theme_custom_special_key_color"
        private const val PREF_KEY_CUSTOM_TEXT = "theme_custom_key_text_color"
        private const val PREF_KEY_CUSTOM_SPECIAL_TEXT = "theme_custom_special_key_text_color"
        private const val PREF_KEY_CUSTOM_ENTER = "theme_custom_enter_key_color"
        private const val PREF_KEY_CUSTOM_ENTER_TEXT = "theme_custom_enter_key_text_color"
        private const val PREF_KEY_CUSTOM_CANDIDATE_TEXT =
            "theme_custom_candidate_text_color"
        private const val PREF_KEY_CUSTOM_CANDIDATE_ITEM_BG =
            "theme_custom_candidate_item_bg_color"
        private const val PREF_KEY_CUSTOM_CANDIDATE_ITEM_PRESSED_BG =
            "theme_custom_candidate_item_pressed_bg_color"
        private const val PREF_KEY_CUSTOM_SHORTCUT_ICON =
            "theme_custom_shortcut_icon_color"

        // Custom Border Keys
        private const val PREF_KEY_CUSTOM_BORDER_ENABLE = "theme_custom_border_enable"
        private const val PREF_KEY_CUSTOM_BORDER_COLOR = "theme_custom_border_color"
        private const val PREF_KEY_CUSTOM_BORDER_WIDTH = "theme_custom_border_width"

        // Custom Input Text Keys
        private const val CATEGORY_KEY_CUSTOM_INPUT = "category_custom_input"
        private const val PREF_KEY_CUSTOM_INPUT_ENABLE = "theme_custom_input_color_enable"
        private const val PREF_KEY_CUSTOM_PRE_EDIT_BG = "theme_custom_pre_edit_bg_color"
        private const val PREF_KEY_CUSTOM_PRE_EDIT_TEXT = "theme_custom_pre_edit_text_color"
        private const val PREF_KEY_CUSTOM_POST_EDIT_BG = "theme_custom_post_edit_bg_color"
        private const val PREF_KEY_CUSTOM_POST_EDIT_TEXT = "theme_custom_post_edit_text_color"

        // Modes
        private const val MODE_DEFAULT = "default"
        private const val MODE_CUSTOM = "custom"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // -------------------------------------------------------
        // System Category
        // -------------------------------------------------------
        val systemCategory = PreferenceCategory(context).apply {
            title = getString(R.string.theme_category_system)
        }
        screen.addPreference(systemCategory)

        // Round Corner Preference
        val roundCornerPref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_ROUND_CORNER
            title = getString(R.string.pref_round_corner_keyboard_title)
            summary = getString(R.string.pref_round_corner_keyboard_summary)
            setDefaultValue(false)
        }
        systemCategory.addPreference(roundCornerPref)

        // Liquid Glass Settings
        val liquidGlassSwitch = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_LIQUID_GLASS
            title = getString(R.string.liquid_glass_effect)
            summary = getString(R.string.enable_glass_blur_effect)
            setDefaultValue(false)
        }
        systemCategory.addPreference(liquidGlassSwitch)

        val liquidGlassBlurPref = SeekBarWithEditTextPreference(context).apply {
            key = PREF_KEY_LIQUID_GLASS_BLUR
            title = getString(R.string.blur_radius)
            min = 0
            max = 255
            setDefaultValue(220)
            showSeekBarValue = true
        }
        systemCategory.addPreference(liquidGlassBlurPref)

        val liquidGlassKeyAlphaPref = SeekBarWithEditTextPreference(context).apply {
            key = PREF_KEY_LIQUID_GLASS_KEY_ALPHA
            title = getString(R.string.key_transparency)
            min = 0
            max = 255
            setDefaultValue(255)
            showSeekBarValue = true
        }
        systemCategory.addPreference(liquidGlassKeyAlphaPref)

        // Liquid Glass Dependency Logic
        val isLiquidGlassEnabled = liquidGlassSwitch.isChecked
        liquidGlassBlurPref.isEnabled = isLiquidGlassEnabled
        liquidGlassKeyAlphaPref.isEnabled = isLiquidGlassEnabled

        liquidGlassSwitch.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            liquidGlassBlurPref.isEnabled = isEnabled
            liquidGlassKeyAlphaPref.isEnabled = isEnabled
            true
        }

        // Default Theme Checkbox
        val defaultPref = CheckBoxPreference(context).apply {
            key = PREF_KEY_DEFAULT
            title = getString(R.string.theme_default)
            summary = getString(R.string.keyboard_theme_summary)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                handleThemeSelection(MODE_DEFAULT)
                if (DynamicColors.isDynamicColorAvailable()) {
                    showColorPickerDialog(
                        initialColor = appPreference.seedColor,
                        onColorSelected = { color ->
                            appPreference.seedColor = color
                            requireActivity().recreate()
                        }
                    )
                }
                true
            }
        }
        systemCategory.addPreference(defaultPref)


        // -------------------------------------------------------
        // Custom Category (Keyboard Appearance)
        // -------------------------------------------------------
        val customCategory = PreferenceCategory(context).apply {
            title = getString(R.string.theme_category_custom)
        }
        screen.addPreference(customCategory)

        // Custom Theme Mode Selection
        val customPref = CheckBoxPreference(context).apply {
            key = PREF_KEY_CUSTOM
            title = getString(R.string.theme_custom)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                handleThemeSelection(MODE_CUSTOM)
                true
            }
        }
        customCategory.addPreference(customPref)

        // Custom Background Color
        val customBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_BG,
            getString(R.string.theme_custom_bg_color)
        ) { appPreference.custom_theme_bg_color }
        customCategory.addPreference(customBgPref)

        // Custom Key Color
        val customKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_KEY,
            getString(R.string.theme_custom_key_color)
        ) { appPreference.custom_theme_key_color }
        customCategory.addPreference(customKeyPref)

        // Custom Special Key Color
        val customSpecialKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SPECIAL_KEY,
            getString(R.string.theme_custom_special_key_color)
        ) { appPreference.custom_theme_special_key_color }
        customCategory.addPreference(customSpecialKeyPref)

        // Custom Text Color
        val customTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_TEXT,
            getString(R.string.theme_custom_key_text_color)
        ) { appPreference.custom_theme_key_text_color }
        customCategory.addPreference(customTextPref)

        // Custom Special Text Color
        val customSpecialTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SPECIAL_TEXT,
            getString(R.string.theme_custom_special_key_text_color)
        ) { appPreference.custom_theme_special_key_text_color }
        customCategory.addPreference(customSpecialTextPref)

        // Custom Enter Key Color
        val customEnterKeyPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_ENTER,
            "確定キーの背景色"
        ) { appPreference.custom_theme_enter_key_color }
        customCategory.addPreference(customEnterKeyPref)

        // Custom Enter Key Text Color
        val customEnterTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_ENTER_TEXT,
            "確定キーの文字色"
        ) { appPreference.custom_theme_enter_key_text_color }
        customCategory.addPreference(customEnterTextPref)

        val customCandidateTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_CANDIDATE_TEXT,
            getString(R.string.theme_custom_candidate_text_color)
        ) { appPreference.custom_theme_candidate_text_color }
        customCategory.addPreference(customCandidateTextPref)

        val customCandidateItemBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_CANDIDATE_ITEM_BG,
            getString(R.string.theme_custom_candidate_item_bg_color)
        ) { appPreference.custom_theme_candidate_item_bg_color }
        customCategory.addPreference(customCandidateItemBgPref)

        val customCandidateItemPressedBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_CANDIDATE_ITEM_PRESSED_BG,
            getString(R.string.theme_custom_candidate_item_pressed_bg_color)
        ) {
            appPreference.getCustomThemeCandidateItemPressedBgColor(
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.qwety_key_bg_color)
            )
        }
        customCategory.addPreference(customCandidateItemPressedBgPref)

        val customShortcutIconPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_SHORTCUT_ICON,
            getString(R.string.theme_custom_shortcut_icon_color)
        ) { appPreference.custom_theme_shortcut_icon_color }
        customCategory.addPreference(customShortcutIconPref)

        // Custom Border Settings
        val customBorderEnablePref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_CUSTOM_BORDER_ENABLE
            title = getString(R.string.custom_border_enable)
            setDefaultValue(false)
        }
        customCategory.addPreference(customBorderEnablePref)

        val customBorderColorPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_BORDER_COLOR,
            getString(R.string.custom_border_color)
        ) { appPreference.custom_theme_border_color }
        customCategory.addPreference(customBorderColorPref)

        // Custom Border Width (修正: 初期値の設定とリスナーの追加)
        val customBorderWidthPref = SeekBarWithEditTextPreference(context).apply {
            key = PREF_KEY_CUSTOM_BORDER_WIDTH
            title = getString(R.string.custom_border_width)
            min = 1
            max = 8
            showSeekBarValue = true

            // AppPreferenceの現在の値をUIにセット
            value = appPreference.custom_theme_border_width

            // 値が変更されたときにAppPreferenceを更新するリスナー
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val width = newValue as Int
                appPreference.custom_theme_border_width = width
                true
            }
        }
        customCategory.addPreference(customBorderWidthPref)

        // Link Border Color & Width capability to switch
        val isBorderEnabled = customBorderEnablePref.isChecked
        customBorderColorPref.isEnabled = isBorderEnabled
        customBorderWidthPref.isEnabled = isBorderEnabled

        customBorderEnablePref.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as Boolean
            customBorderColorPref.isEnabled = isEnabled
            customBorderWidthPref.isEnabled = isEnabled
            true
        }


        // -------------------------------------------------------
        // Input Text Category
        // -------------------------------------------------------
        val inputCategory = PreferenceCategory(context).apply {
            key = CATEGORY_KEY_CUSTOM_INPUT
            title = getString(R.string.composing_text)
        }
        screen.addPreference(inputCategory)

        // 0. Enable Custom Input Colors
        val inputColorEnablePref = SwitchPreferenceCompat(context).apply {
            key = PREF_KEY_CUSTOM_INPUT_ENABLE
            title = getString(R.string.custom_input_color_enable_title)
            setDefaultValue(false)
        }
        inputCategory.addPreference(inputColorEnablePref)

        // 1. Pre-Edit Background
        val preEditBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_PRE_EDIT_BG,
            getString(R.string.pre_edit)
        ) { appPreference.custom_theme_pre_edit_bg_color }
        inputCategory.addPreference(preEditBgPref)

        // 2. Pre-Edit Text Color
        val preEditTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_PRE_EDIT_TEXT,
            getString(R.string.pre_edit_text)
        ) { appPreference.custom_theme_pre_edit_text_color }
        inputCategory.addPreference(preEditTextPref)

        // 3. Post-Edit/Highlight Background
        val postEditBgPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_POST_EDIT_BG,
            getString(R.string.conversion_text_color)
        ) { appPreference.custom_theme_post_edit_bg_color }
        inputCategory.addPreference(postEditBgPref)

        // 4. Post-Edit/Highlight Text Color
        val postEditTextPref = createColorPreference(
            context,
            PREF_KEY_CUSTOM_POST_EDIT_TEXT,
            getString(R.string.conversion_text_color_)
        ) { appPreference.custom_theme_post_edit_text_color }
        inputCategory.addPreference(postEditTextPref)

        // Link Color Preferences to Enable Switch
        fun updateInputColorPrefsState(enabled: Boolean) {
            preEditBgPref.isEnabled = enabled
            preEditTextPref.isEnabled = enabled
            postEditBgPref.isEnabled = enabled
            postEditTextPref.isEnabled = enabled
        }

        // Initialize state
        updateInputColorPrefsState(inputColorEnablePref.isChecked)

        // Listener
        inputColorEnablePref.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            updateInputColorPrefsState(enabled)
            true
        }

        // -------------------------------------------------------
        // Custom Font & Custom Icon Category
        // -------------------------------------------------------
        // -------------------------------------------------------
        // Custom Font & Custom Icon Category
        // -------------------------------------------------------
        val fontCategory = PreferenceCategory(context).apply {
            title = "カスタムフォントの設定"
        }
        screen.addPreference(fontCategory)

        val fontKeySelectPref = Preference(context).apply {
            key = "custom_font_key_select"
            title = "キーボードフォントのインポート"
        }
        fontCategory.addPreference(fontKeySelectPref)

        val fontCandidateSelectPref = Preference(context).apply {
            key = "custom_font_candidate_select"
            title = "変換候補フォントのインポート"
        }
        fontCategory.addPreference(fontCandidateSelectPref)

        val iconCategory = PreferenceCategory(context).apply {
            title = "特殊キーの見た目のカスタマイズ"
        }
        screen.addPreference(iconCategory)

        // 統合された特殊キーの見た目設定
        val modeSwitchPref = Preference(context).apply {
            key = "custom_key_appearance_mode_switch"
            title = "「あa1 / 地球儀」キーの見た目"
            updateSpecialKeySummary(this, "mode_switch")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "mode_switch", selectModeSwitchLauncher)
                true
            }
        }
        iconCategory.addPreference(modeSwitchPref)

        val emojiPref = Preference(context).apply {
            key = "custom_key_appearance_emoji"
            title = "「記号 / 絵文字」キーの見た目"
            updateSpecialKeySummary(this, "emoji")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "emoji", selectEmojiLauncher)
                true
            }
        }
        iconCategory.addPreference(emojiPref)

        val deletePref = Preference(context).apply {
            key = "custom_key_appearance_delete"
            title = "「削除 (Backspace)」キーの見た目"
            updateSpecialKeySummary(this, "delete")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "delete", selectDeleteLauncher)
                true
            }
        }
        iconCategory.addPreference(deletePref)

        val undoPref = Preference(context).apply {
            key = "custom_key_appearance_undo"
            title = "「戻る (Undo)」キーの見た目"
            updateSpecialKeySummary(this, "undo")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "undo", selectUndoLauncher)
                true
            }
        }
        iconCategory.addPreference(undoPref)

        val enterPref = Preference(context).apply {
            key = "custom_key_appearance_enter"
            title = "「確定 (Enter)」キーの見た目"
            updateSpecialKeySummary(this, "enter")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "enter", selectEnterLauncher)
                true
            }
        }
        iconCategory.addPreference(enterPref)

        val spacePref = Preference(context).apply {
            key = "custom_key_appearance_space"
            title = "「スペース (変換)」キーの見た目"
            updateSpecialKeySummary(this, "space")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "space", selectSpaceLauncher)
                true
            }
        }
        iconCategory.addPreference(spacePref)

        val symbolPref = Preference(context).apply {
            key = "custom_key_appearance_symbol"
            title = "「記号」キーのテキスト"
            updateSpecialKeySummary(this, "symbol")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "symbol", null)
                true
            }
        }
        iconCategory.addPreference(symbolPref)

        val text123Pref = Preference(context).apply {
            key = "custom_key_appearance_123"
            title = "「123」キーのテキスト"
            updateSpecialKeySummary(this, "123")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "123", null)
                true
            }
        }
        iconCategory.addPreference(text123Pref)

        val iconLeftPref = Preference(context).apply {
            key = "custom_key_appearance_left"
            title = "「左移動 (←)」キーの画像"
            updateSpecialKeySummary(this, "left")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "left", selectLeftLauncher)
                true
            }
        }
        iconCategory.addPreference(iconLeftPref)

        val iconRightPref = Preference(context).apply {
            key = "custom_key_appearance_right"
            title = "「右移動 (→)」キーの画像"
            updateSpecialKeySummary(this, "right")
            setOnPreferenceClickListener {
                showSpecialKeyAppearanceDialog(this, "right", selectRightLauncher)
                true
            }
        }
        iconCategory.addPreference(iconRightPref)

        preferenceScreen = screen

        setupFontPreference("custom_font_key_select", "custom_font_key")
        setupFontPreference("custom_font_candidate_select", "custom_font_candidate")

        // Initialize state based on current preference
        updateCheckStates(appPreference.theme_mode)
        updateCustomColorsVisibility(appPreference.theme_mode == MODE_CUSTOM)
    }

    private fun createColorPreference(
        context: Context,
        key: String,
        titleStr: String,
        colorProvider: () -> Int
    ): Preference {
        return Preference(context).apply {
            this.key = key
            title = titleStr
            summary = String.format("#%08X", colorProvider())
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showColorPickerDialog(
                    initialColor = colorProvider(),
                    onColorSelected = { color ->
                        saveCustomColor(key, color)
                        summary = String.format("#%08X", color)
                    }
                )
                true
            }
        }
    }

    private fun handleThemeSelection(mode: String) {
        appPreference.theme_mode = mode
        updateCheckStates(mode)
        updateCustomColorsVisibility(mode == MODE_CUSTOM)
    }

    private fun updateCheckStates(selectedMode: String) {
        findPreference<CheckBoxPreference>(PREF_KEY_DEFAULT)?.isChecked =
            selectedMode == MODE_DEFAULT
        findPreference<CheckBoxPreference>(PREF_KEY_CUSTOM)?.isChecked = selectedMode == MODE_CUSTOM
    }

    private fun updateCustomColorsVisibility(isVisible: Boolean) {
        // Base Custom Colors
        findPreference<Preference>(PREF_KEY_CUSTOM_BG)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_KEY)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SPECIAL_KEY)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_TEXT)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SPECIAL_TEXT)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_ENTER)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_ENTER_TEXT)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_CANDIDATE_TEXT)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_CANDIDATE_ITEM_BG)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_CANDIDATE_ITEM_PRESSED_BG)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_SHORTCUT_ICON)?.isVisible = isVisible

        // Border Settings
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_ENABLE)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_COLOR)?.isVisible = isVisible
        findPreference<Preference>(PREF_KEY_CUSTOM_BORDER_WIDTH)?.isVisible = isVisible

        // Input Category Visibility
        //findPreference<PreferenceCategory>(CATEGORY_KEY_CUSTOM_INPUT)?.isVisible = isVisible
    }

    private fun saveCustomColor(key: String, color: Int) {
        when (key) {
            // Base Colors
            PREF_KEY_CUSTOM_BG -> appPreference.custom_theme_bg_color = color
            PREF_KEY_CUSTOM_KEY -> appPreference.custom_theme_key_color = color
            PREF_KEY_CUSTOM_SPECIAL_KEY -> appPreference.custom_theme_special_key_color = color
            PREF_KEY_CUSTOM_TEXT -> appPreference.custom_theme_key_text_color = color
            PREF_KEY_CUSTOM_SPECIAL_TEXT -> appPreference.custom_theme_special_key_text_color =
                color
            PREF_KEY_CUSTOM_ENTER -> appPreference.custom_theme_enter_key_color = color
            PREF_KEY_CUSTOM_ENTER_TEXT -> appPreference.custom_theme_enter_key_text_color = color
            PREF_KEY_CUSTOM_CANDIDATE_TEXT -> appPreference.custom_theme_candidate_text_color =
                color
            PREF_KEY_CUSTOM_CANDIDATE_ITEM_BG ->
                appPreference.custom_theme_candidate_item_bg_color = color
            PREF_KEY_CUSTOM_CANDIDATE_ITEM_PRESSED_BG ->
                appPreference.custom_theme_candidate_item_pressed_bg_color = color
            PREF_KEY_CUSTOM_SHORTCUT_ICON -> appPreference.custom_theme_shortcut_icon_color =
                color

            // Border Color
            PREF_KEY_CUSTOM_BORDER_COLOR -> appPreference.custom_theme_border_color = color

            // Input Colors
            PREF_KEY_CUSTOM_PRE_EDIT_BG -> appPreference.custom_theme_pre_edit_bg_color = color
            PREF_KEY_CUSTOM_PRE_EDIT_TEXT -> appPreference.custom_theme_pre_edit_text_color = color
            PREF_KEY_CUSTOM_POST_EDIT_BG -> appPreference.custom_theme_post_edit_bg_color = color
            PREF_KEY_CUSTOM_POST_EDIT_TEXT -> appPreference.custom_theme_post_edit_text_color =
                color
        }
    }

    @SuppressLint("CheckResult")
    private fun showColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit) {
        MaterialDialog(requireContext()).show {
            title(text = getString(R.string.keyboard_theme_dialog_title_2))
            colorChooser(
                colors = intArrayOf(
                    0x00000000,
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.blue),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.char_in_edit_color
                    ),
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.orange),
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.violet),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_dark
                    ),
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.mint),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_dark
                    ),
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.sky),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange2
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_dark
                    ),
                    android.graphics.Color.WHITE,
                    android.graphics.Color.BLACK,
                    android.graphics.Color.DKGRAY,
                    android.graphics.Color.LTGRAY,
                    "#1C1C1E".toColorInt(),
                    "#1E2022".toColorInt()
                ),
                initialSelection = initialColor,
                allowCustomArgb = true
            ) { _, color ->
                onColorSelected(color)
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }

    private fun showSpecialKeyAppearanceDialog(pref: Preference, type: String, launcher: ActivityResultLauncher<String>?) {
        val context = requireContext()
        val currentText = when (type) {
            "mode_switch" -> appPreference.custom_text_mode_switch
            "undo" -> appPreference.custom_text_undo
            "emoji" -> appPreference.custom_text_emoji
            "delete" -> appPreference.custom_text_delete
            "enter" -> appPreference.custom_text_enter
            "space" -> appPreference.custom_text_space
            "symbol" -> appPreference.custom_text_symbol
            "123" -> appPreference.custom_text_123
            else -> ""
        }

        val options = mutableListOf<String>()
        val supportsText = type != "left" && type != "right"
        if (supportsText) {
            options.add("カスタムテキストを設定する")
        }
        if (launcher != null) {
            options.add("カスタム画像を設定する")
        }
        options.add("デフォルトに戻す")

        AlertDialog.Builder(context)
            .setTitle(pref.title)
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    "カスタムテキストを設定する" -> {
                        showTextInputDialog(pref, type, currentText)
                    }
                    "カスタム画像を設定する" -> {
                        launcher?.launch("image/*")
                    }
                    "デフォルトに戻す" -> {
                        resetSpecialKey(type)
                        updateSpecialKeySummary(pref, type)
                    }
                }
            }
            .show()
    }

    private fun showTextInputDialog(pref: Preference, type: String, currentText: String) {
        val context = requireContext()
        val editText = android.widget.EditText(context).apply {
            setText(currentText)
            setSelection(currentText.length)
            hint = "表示するテキストを入力してください"
        }

        AlertDialog.Builder(context)
            .setTitle(pref.title)
            .setView(editText)
            .setPositiveButton("設定") { _, _ ->
                val newText = editText.text.toString().trim()
                saveSpecialKeyText(type, newText)
                updateSpecialKeySummary(pref, type)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun saveSpecialKeyText(type: String, text: String) {
        when (type) {
            "mode_switch" -> {
                appPreference.custom_text_mode_switch = text
                appPreference.custom_icon_mode_switch_path = ""
            }
            "undo" -> {
                appPreference.custom_text_undo = text
                appPreference.custom_icon_undo_path = ""
            }
            "emoji" -> {
                appPreference.custom_text_emoji = text
                appPreference.custom_icon_emoji_path = ""
            }
            "delete" -> {
                appPreference.custom_text_delete = text
                appPreference.custom_icon_delete_path = ""
            }
            "enter" -> {
                appPreference.custom_text_enter = text
                appPreference.custom_icon_enter_path = ""
            }
            "space" -> {
                appPreference.custom_text_space = text
                appPreference.custom_icon_space_path = ""
            }
            "symbol" -> {
                appPreference.custom_text_symbol = text
            }
            "123" -> {
                appPreference.custom_text_123 = text
            }
        }
    }

    private fun resetSpecialKey(type: String) {
        when (type) {
            "mode_switch" -> {
                appPreference.custom_text_mode_switch = ""
                appPreference.custom_icon_mode_switch_path = ""
            }
            "undo" -> {
                appPreference.custom_text_undo = ""
                appPreference.custom_icon_undo_path = ""
            }
            "emoji" -> {
                appPreference.custom_text_emoji = ""
                appPreference.custom_icon_emoji_path = ""
            }
            "delete" -> {
                appPreference.custom_text_delete = ""
                appPreference.custom_icon_delete_path = ""
            }
            "enter" -> {
                appPreference.custom_text_enter = ""
                appPreference.custom_icon_enter_path = ""
            }
            "space" -> {
                appPreference.custom_text_space = ""
                appPreference.custom_icon_space_path = ""
            }
            "symbol" -> {
                appPreference.custom_text_symbol = ""
            }
            "123" -> {
                appPreference.custom_text_123 = ""
            }
            "left" -> {
                appPreference.custom_icon_arrow_left_path = ""
            }
            "right" -> {
                appPreference.custom_icon_arrow_right_path = ""
            }
        }
    }

    private fun updateSpecialKeySummary(pref: Preference, type: String) {
        val hasImage = when (type) {
            "enter" -> appPreference.custom_icon_enter_path.isNotEmpty()
            "space" -> appPreference.custom_icon_space_path.isNotEmpty()
            "left" -> appPreference.custom_icon_arrow_left_path.isNotEmpty()
            "right" -> appPreference.custom_icon_arrow_right_path.isNotEmpty()
            "mode_switch" -> appPreference.custom_icon_mode_switch_path.isNotEmpty()
            "undo" -> appPreference.custom_icon_undo_path.isNotEmpty()
            "emoji" -> appPreference.custom_icon_emoji_path.isNotEmpty()
            "delete" -> appPreference.custom_icon_delete_path.isNotEmpty()
            else -> false
        }

        val currentText = when (type) {
            "mode_switch" -> appPreference.custom_text_mode_switch
            "undo" -> appPreference.custom_text_undo
            "emoji" -> appPreference.custom_text_emoji
            "delete" -> appPreference.custom_text_delete
            "enter" -> appPreference.custom_text_enter
            "space" -> appPreference.custom_text_space
            "symbol" -> appPreference.custom_text_symbol
            "123" -> appPreference.custom_text_123
            else -> ""
        }

        if (currentText.isNotEmpty()) {
            pref.summary = "設定中: [テキスト] \"$currentText\""
        } else if (hasImage) {
            val path = when (type) {
                "enter" -> appPreference.custom_icon_enter_path
                "space" -> appPreference.custom_icon_space_path
                "left" -> appPreference.custom_icon_arrow_left_path
                "right" -> appPreference.custom_icon_arrow_right_path
                "mode_switch" -> appPreference.custom_icon_mode_switch_path
                "undo" -> appPreference.custom_icon_undo_path
                "emoji" -> appPreference.custom_icon_emoji_path
                "delete" -> appPreference.custom_icon_delete_path
                else -> ""
            }
            pref.summary = "設定中: [画像] ${File(path).name} （※再起動後に適用）"
        } else {
            pref.summary = "デフォルト（未設定）"
        }
    }

    private fun setupFontPreference(prefKey: String, destFileName: String) {
        findPreference<Preference>(prefKey)?.apply {
            setOnPreferenceClickListener {
                val currentPath = when (prefKey) {
                    "custom_font_key_select" -> appPreference.custom_font_key_path
                    "custom_font_candidate_select" -> appPreference.custom_font_candidate_path
                    else -> ""
                }
                val launcher = when (prefKey) {
                    "custom_font_key_select" -> selectFontKeyLauncher
                    "custom_font_candidate_select" -> selectFontCandidateLauncher
                    else -> null
                }
                if (currentPath.isNotEmpty() && File(currentPath).exists()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setItems(arrayOf("新しいフォントに変更する", "デフォルトフォントに戻す")) { _, which ->
                            when (which) {
                                0 -> launcher?.launch("*/*")
                                1 -> {
                                    File(currentPath).delete()
                                    when (prefKey) {
                                        "custom_font_key_select" -> appPreference.custom_font_key_path = ""
                                        "custom_font_candidate_select" -> appPreference.custom_font_candidate_path = ""
                                    }
                                    updateFontSummary(this, "")
                                }
                            }
                        }
                        .show()
                } else {
                    launcher?.launch("*/*")
                }
                true
            }
            val currentPath = when (prefKey) {
                "custom_font_key_select" -> appPreference.custom_font_key_path
                "custom_font_candidate_select" -> appPreference.custom_font_candidate_path
                else -> ""
            }
            updateFontSummary(this, currentPath)
        }
    }

    private fun updateFontSummary(pref: Preference, path: String) {
        if (path.isNotEmpty() && File(path).exists()) {
            pref.summary = "設定中: ${File(path).name}"
        } else {
            when (pref.key) {
                "custom_font_key_select" -> pref.summary = "キーボード上の文字専用のフォントファイルを適用します"
                "custom_font_candidate_select" -> pref.summary = "変換候補（サジェスト）専用のフォントファイルを適用します"
            }
        }
    }

    private fun handleFontSelected(uri: Uri, prefKey: String, destFileName: String) {
        val path = copyFontUriToInternalStorage(uri, destFileName)
        if (path != null) {
            when (prefKey) {
                "custom_font_key_select" -> appPreference.custom_font_key_path = path
                "custom_font_candidate_select" -> appPreference.custom_font_candidate_path = path
            }
            findPreference<Preference>(prefKey)?.let { pref ->
                updateFontSummary(pref, path)
            }
        } else {
            Toast.makeText(context, "フォントのコピーに失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyFontUriToInternalStorage(uri: Uri, destFileName: String): String? {
        val context = context ?: return null
        val fontsDir = File(context.filesDir, "fonts")
        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }
        val extension = getExtension(uri) ?: "ttf"
        val destFile = File(fontsDir, "$destFileName.$extension")

        fontsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(destFileName)) {
                file.delete()
            }
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun handleImageSelected(uri: Uri, destFileName: String, type: String) {
        val path = copyUriToInternalStorage(uri, destFileName)
        if (path != null) {
            when (type) {
                "enter" -> appPreference.custom_icon_enter_path = path
                "space" -> appPreference.custom_icon_space_path = path
                "left" -> appPreference.custom_icon_arrow_left_path = path
                "right" -> appPreference.custom_icon_arrow_right_path = path
                "mode_switch" -> appPreference.custom_icon_mode_switch_path = path
                "undo" -> appPreference.custom_icon_undo_path = path
                "emoji" -> appPreference.custom_icon_emoji_path = path
                "delete" -> appPreference.custom_icon_delete_path = path
            }
            findPreference<Preference>("custom_icon_${type}_select")?.let { pref ->
                updateSpecialKeySummary(pref, type)
            }
        } else {
            Toast.makeText(context, "画像のコピーに失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToInternalStorage(uri: Uri, destFileName: String): String? {
        val context = context ?: return null
        val iconsDir = File(context.filesDir, "icons")
        if (!iconsDir.exists()) {
            iconsDir.mkdirs()
        }
        val extension = getExtension(uri) ?: "png"
        val destFile = File(iconsDir, "$destFileName.$extension")

        iconsDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(destFileName)) {
                file.delete()
            }
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getExtension(uri: Uri): String? {
        val context = context ?: return null
        if (uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            }
        }
        return MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    }
}
