package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingMainBinding
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SettingMainFragment : Fragment() {

    private var _binding: FragmentSettingMainBinding? = null
    private val binding get() = _binding!!

    private var tabLayoutMediator: TabLayoutMediator? = null
    private lateinit var searchAdapter: SettingsSearchResultAdapter
    private var allSearchEntries: List<SettingsPreferenceIndex.Entry> = emptyList()
    private var pendingScrollPreferenceKey: String? = null

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var userDictionaryRepository: UserDictionaryRepository

    @Inject
    lateinit var romajiMapRepository: RomajiMapRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val romajiMapUpdated = appPreference.romaji_map_data_version
        lifecycleScope.launch(Dispatchers.IO) {
            if (romajiMapUpdated == 0) {
                romajiMapRepository.updateDefaultMap()

                userDictionaryRepository.apply {
                    if (searchByReadingExactMatchSuspend("びゃんびゃんめん").isEmpty()) {
                        insert(
                            com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord(
                                reading = "びゃんびゃんめん",
                                word = "\uD883\uDEDE\uD883\uDEDE麺",
                                posIndex = 0,
                                posScore = 4000,
                            ),
                        )
                    }
                    if (searchByReadingExactMatchSuspend("びゃん").isEmpty()) {
                        insert(
                            com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord(
                                reading = "びゃん",
                                word = "\uD883\uDEDE",
                                posIndex = 0,
                                posScore = 3000,
                            ),
                        )
                    }
                }

                appPreference.romaji_map_data_version = 1
            }
        }

        val adapter = SettingPagerAdapter(this)
        binding.settingViewPager.adapter = adapter

        tabLayoutMediator =
            TabLayoutMediator(binding.settingTabLayout, binding.settingViewPager) { tab, position ->
                tab.text = adapter.getTitle(position, this)
            }
        tabLayoutMediator?.attach()

        setupSettingsSearch()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.settingsSearchResults.isVisible) {
                        clearSettingsSearch()
                        return
                    }
                    requireActivity().finish()
                }
            },
        )
    }

    private fun setupSettingsSearch() {
        allSearchEntries = SettingsPreferenceIndex.load(requireContext())
        searchAdapter = SettingsSearchResultAdapter { entry ->
            handleSearchSelection(entry)
        }
        binding.settingsSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.settingsSearchResults.adapter = searchAdapter

        binding.settingsSearchInput.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty()
            if (query.isBlank()) {
                clearSettingsSearch()
                return@doAfterTextChanged
            }
            val results = allSearchEntries.filter { it.matches(query) }.take(40)
            searchAdapter.submitList(results)
            binding.settingsSearchResults.isVisible = results.isNotEmpty()
            binding.settingTabLayout.isVisible = false
        }
    }

    private fun clearSettingsSearch() {
        binding.settingsSearchInput.setText("")
        binding.settingsSearchResults.isVisible = false
        binding.settingTabLayout.isVisible = true
        binding.settingsSearchInput.clearFocus()
    }

    private fun handleSearchSelection(entry: SettingsPreferenceIndex.Entry) {
        clearSettingsSearch()
        entry.navigationActionId?.let { actionId ->
            navigateSafely(actionId)
            return
        }
        pendingScrollPreferenceKey = entry.preferenceKey
        binding.settingViewPager.setCurrentItem(entry.tabIndex, true)
        binding.settingViewPager.post {
            scrollToPreferenceInTab(entry.tabIndex, entry.preferenceKey)
            pendingScrollPreferenceKey = null
        }
    }

    private fun scrollToPreferenceInTab(tabIndex: Int, preferenceKey: String?) {
        if (preferenceKey.isNullOrBlank()) return
        val fragment = childFragmentManager.findFragmentByTag("f$tabIndex") as? PreferenceFragmentCompat
        fragment?.scrollToPreference(preferenceKey)
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            binding.settingProgressBar.isVisible = true
            val enabled = withContext(Dispatchers.IO) {
                isKeyboardBoardEnabled()
            }
            binding.settingProgressBar.isVisible = false
            if (enabled == false) {
                navigateSafely(
                    R.id.action_navigation_setting_to_enableKeyboardFragment,
                )
            }
        }
    }

    override fun onDestroyView() {
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        binding.settingViewPager.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun isKeyboardBoardEnabled(): Boolean? {
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)
        return imm?.enabledInputMethodList?.any { it.packageName == requireContext().packageName }
    }
}
