package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLearningMemoryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DictionaryPreferenceFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var learnRepository: LearnRepository

    @Inject
    lateinit var learningMemoryRepository: AzooKeyLearningMemoryRepository

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_dictionary, rootKey)

        val ngWordSwitchPreference =
            findPreference<SwitchPreferenceCompat>("ng_word_enable_preference")
        ngWordSwitchPreference?.apply {
            title = if (isChecked) {
                getString(R.string.ng_word_enable_title_on)
            } else {
                getString(R.string.ng_word_enable_title_off)
            }
            setOnPreferenceChangeListener { _, newValue ->
                title = if (newValue == true) {
                    getString(R.string.ng_word_enable_title_on)
                } else {
                    getString(R.string.ng_word_enable_title_off)
                }
                true
            }
        }

        findPreference<Preference>("ng_word_preference")?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.action_navigation_setting_to_ngWordFragment
            )
            true
        }

        findPreference<Preference>("system_user_dictionary_builder_preference")?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.action_navigation_setting_to_systemUserDictionaryBuilderFragment
            )
            true
        }

        findPreference<Preference>("candidate_order_override_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.action_navigation_setting_to_candidateOrderOverrideFragment)
            true
        }

        findPreference<Preference>("external_dictionary_settings_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.action_navigation_setting_to_externalDictionarySettingsFragment)
            true
        }

        findPreference<Preference>("learn_dictionary_view_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.navigation_learn_dictionary)
            true
        }

        findPreference<Preference>("clear_learning_data_preference")?.setOnPreferenceClickListener {
            showClearLearningDataDialog()
            true
        }

        findPreference<Preference>("ngram_rule_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.action_navigation_setting_to_ngramRuleFragment)
            true
        }

        val learnDictionaryPrefixSeekBar =
            findPreference<SeekBarPreference>("learn_prediction_preference")
        learnDictionaryPrefixSeekBar?.apply {
            appPreference.learn_prediction_preference.let {
                summary = resources.getString(R.string.learn_dictionary_prefix_match_summary, it)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary =
                    resources.getString(
                        R.string.learn_dictionary_prefix_match_summary,
                        newValue as Int
                    )
                true
            }
        }

        val userDictionaryPrefixSeekBar =
            findPreference<SeekBarPreference>("user_dictionary_prefix_match_number")
        userDictionaryPrefixSeekBar?.apply {
            appPreference.user_dictionary_prefix_match_number_preference?.let {
                summary = resources.getString(R.string.user_dictionary_prefix_match_summary, it)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary =
                    resources.getString(
                        R.string.user_dictionary_prefix_match_summary,
                        newValue as Int
                    )
                true
            }
        }

    }

    private fun showClearLearningDataDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_learning_data_title))
            .setMessage(getString(R.string.clear_learning_data_confirm_message))
            .setPositiveButton(getString(R.string.yes_string)) { _, _ ->
                lifecycleScope.launch {
                    learnRepository.deleteAll()
                    runCatching { learningMemoryRepository.rebuildLoudsFromRoom() }
                    android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.clear_learning_data_success),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.no_string), null)
            .show()
    }
}
