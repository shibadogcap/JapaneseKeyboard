package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.ContentResolver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream

import javax.inject.Inject

@AndroidEntryPoint
class CustomKeyboardPreferenceFragment : PreferenceFragmentCompat() {

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
    private val selectFontKeyLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFontSelected(it, "custom_font_key_select", "custom_font_key") }
    }
    private val selectFontCandidateLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFontSelected(it, "custom_font_candidate_select", "custom_font_candidate") }
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_custom, rootKey)

        val customKeyboardSizePreference =
            findPreference<Preference>("custom_keyboard_size_preference")

        customKeyboardSizePreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.action_navigation_setting_to_flickKeyboardSizeSettingsFragment)
                true
            }
        }

        findPreference<Preference>("flick_keyboard_popup_view_style_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(R.id.action_navigation_setting_to_flickKeyboardPopupStyleListFragment)
                true
            }
        }

        setupIconPreference(
            prefKey = "custom_icon_enter_select",
            launcher = selectEnterLauncher,
            destFileName = "custom_enter_icon",
            type = "enter"
        )
        setupIconPreference(
            prefKey = "custom_icon_space_select",
            launcher = selectSpaceLauncher,
            destFileName = "custom_space_icon",
            type = "space"
        )
        setupIconPreference(
            prefKey = "custom_icon_arrow_left_select",
            launcher = selectLeftLauncher,
            destFileName = "custom_left_arrow_icon",
            type = "left"
        )
        setupIconPreference(
            prefKey = "custom_icon_arrow_right_select",
            launcher = selectRightLauncher,
            destFileName = "custom_right_arrow_icon",
            type = "right"
        )
        setupIconPreference(
            prefKey = "custom_icon_mode_switch_select",
            launcher = selectModeSwitchLauncher,
            destFileName = "custom_mode_switch_icon",
            type = "mode_switch"
        )
        setupIconPreference(
            prefKey = "custom_icon_undo_select",
            launcher = selectUndoLauncher,
            destFileName = "custom_undo_icon",
            type = "undo"
        )
        setupIconPreference(
            prefKey = "custom_icon_emoji_select",
            launcher = selectEmojiLauncher,
            destFileName = "custom_emoji_icon",
            type = "emoji"
        )
        setupIconPreference(
            prefKey = "custom_icon_delete_select",
            launcher = selectDeleteLauncher,
            destFileName = "custom_delete_icon",
            type = "delete"
        )

        setupFontPreference("custom_font_key_select", "custom_font_key")
        setupFontPreference("custom_font_candidate_select", "custom_font_candidate")
    }


    private fun setupIconPreference(
        prefKey: String,
        launcher: ActivityResultLauncher<String>,
        destFileName: String,
        type: String
    ) {
        findPreference<Preference>(prefKey)?.apply {
            setOnPreferenceClickListener {
                val currentPath = when (type) {
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
                if (currentPath.isNotEmpty() && File(currentPath).exists()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setItems(arrayOf("新しい画像に変更する", "元のアイコンに戻す")) { _, which ->
                            when (which) {
                                0 -> launcher.launch("image/*")
                                1 -> {
                                    File(currentPath).delete()
                                    when (type) {
                                        "enter" -> appPreference.custom_icon_enter_path = ""
                                        "space" -> appPreference.custom_icon_space_path = ""
                                        "left" -> appPreference.custom_icon_arrow_left_path = ""
                                        "right" -> appPreference.custom_icon_arrow_right_path = ""
                                        "mode_switch" -> appPreference.custom_icon_mode_switch_path = ""
                                        "undo" -> appPreference.custom_icon_undo_path = ""
                                        "emoji" -> appPreference.custom_icon_emoji_path = ""
                                        "delete" -> appPreference.custom_icon_delete_path = ""
                                    }
                                    updateSummary(this, "")
                                }
                            }
                        }
                        .show()
                } else {
                    launcher.launch("image/*")
                }
                true
            }

            val currentPath = when (type) {
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
            updateSummary(this, currentPath)
        }
    }

    private fun updateSummary(pref: Preference, path: String) {
        if (path.isNotEmpty() && File(path).exists()) {
            pref.summary = "設定中: ${File(path).name}"
            pref.icon = Drawable.createFromPath(path)
        } else {
            pref.icon = null
            when (pref.key) {
                "custom_icon_enter_select" -> pref.summary = "確定キー（エンターキー）のカスタム画像（PNG, JPG, SVG）を設定します"
                "custom_icon_space_select" -> pref.summary = "変換キー（スペースキー）のカスタム画像（PNG, JPG, SVG）を設定します"
                "custom_icon_arrow_left_select" -> pref.summary = "左カーソル移動キーのカスタム画像（PNG, JPG, SVG）を設定します"
                "custom_icon_arrow_right_select" -> pref.summary = "右カーソル移動キーのカスタム画像（PNG, JPG, SVG）を設定します"
                "custom_icon_mode_switch_select" -> pref.summary = "「あa1」モード切替キーのカスタム画像を設定します"
                "custom_icon_undo_select" -> pref.summary = "編集取り消しキーのカスタム画像を設定します"
                "custom_icon_emoji_select" -> pref.summary = "絵文字キーのカスタム画像を設定します"
                "custom_icon_delete_select" -> pref.summary = "バックスペースキーのカスタム画像を設定します"
                "custom_font_key_select" -> pref.summary = "キーボード上の文字専用のフォントファイルを適用します"
                "custom_font_candidate_select" -> pref.summary = "変換候補（サジェスト）専用のフォントファイルを適用します"
            }
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
                                    updateSummary(this, "")
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
            updateSummary(this, currentPath)
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
                updateSummary(pref, path)
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
                updateSummary(pref, path)
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
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            }
        }
        return MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    }
}
