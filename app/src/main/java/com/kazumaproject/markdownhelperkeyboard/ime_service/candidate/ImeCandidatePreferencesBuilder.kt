package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyConversionDefaults
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

/**
 * [ImePreferencesSnapshot] + ランタイム状態から [ImeCandidatePreferences] を組み立てる。
 * AzooKey 本家の request options 相当を IME から切り離す。
 */
object ImeCandidatePreferencesBuilder {
    fun build(
        snapshot: ImePreferencesSnapshot,
        runtime: ImeCandidateRuntimeSession,
        appPreference: AppPreference,
        ngWords: List<String>,
        ngWordPattern: Regex,
        romanize: (String) -> String?,
        toHankakuAlphabet: (String) -> String,
        zenzaiEnabled: Boolean,
        zenzProfile: String = "",
        zenzLeftSideContext: String = "",
        zenzRightSideContext: String = "",
        zenzModelIdentity: String = "",
    ): ImeCandidatePreferences {
        return ImeCandidatePreferences(
            nBest = AzooKeyConversionDefaults.N_BEST,
            useUserDictionary = snapshot.isUserDictionaryEnable,
            useUserTemplate = snapshot.isUserTemplateEnable,
            useRomajiCandidates = snapshot.conversionCandidatesRomajiEnablePreference,
            useBunsetsu = snapshot.bunsetsuSeparation,
            useOmissionSearch = false,
            learningType = learningTypeFromSnapshot(snapshot),
            zenzaiMode = if (zenzaiEnabled) {
                AzooKeyStyleZenzaiMode.On
            } else {
                AzooKeyStyleZenzaiMode.Off
            },
            liveConversionMode = if (snapshot.isLiveConversionEnable) {
                AzooKeyLiveConversionMode.Enabled
            } else {
                AzooKeyLiveConversionMode.Disabled
            },
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
            privacy = CandidateRequestPrivacy(
                isPrivateMode = runtime.isPrivateMode,
                suppressSuggestions = runtime.suppressSuggestions,
            ),
            versionString = "JapaneseKeyboard Version ${BuildConfig.VERSION_NAME}",
            learnedPrefixMatchThreshold = (snapshot.learnPredictionPreference - 1).coerceAtLeast(0),
            userDictionaryPrefixMatchThreshold = (snapshot.userDictionaryPrefixMatchNumber - 1)
                .coerceAtLeast(0),
            isLearnDictionaryMode = snapshot.isLearnDictionaryMode,
            romanize = romanize,
            toHankakuAlphabet = toHankakuAlphabet,
            onNormalBunsetsuResult = runtime.onNormalBunsetsuResult,
            isNgWordFilterEnabled = snapshot.isNgWordEnable,
            ngWords = ngWords,
            ngWordPattern = ngWordPattern,
            isOrderOverrideEnabled = appPreference.candidate_order_override_enable_preference == true,
            zenzProfile = zenzProfile,
            zenzLeftSideContext = zenzLeftSideContext,
            zenzRightSideContext = zenzRightSideContext,
            zenzModelIdentity = zenzModelIdentity,
            experimentalZenzaiPredictiveInput = zenzaiEnabled &&
                snapshot.experimentalZenzaiPredictiveInputPreference,
            isCandidateSelectionActive = runtime.isCandidateSelectionActive,
            isConverting = runtime.isConverting,
            isDirectInputMode = runtime.isDirectInputMode,
            englishCandidateInRoman2KanaInput = snapshot.conversionCandidatesRomajiEnablePreference,
        )
    }

    /**
     * AzooKey [LearningTypeSetting] に準拠。
     * - input_and_output: 学習する（デフォルト）
     * - only_output: 新たな学習を停止（既存 memory は使用）
     * - nothing: これまでの学習も反映しない
     */
    fun learningTypeFromSnapshot(snapshot: ImePreferencesSnapshot): AzooKeyStyleLearningType {
        if (!snapshot.isLearnDictionaryMode) {
            return AzooKeyStyleLearningType.Nothing
        }
        return when (snapshot.learningTypePreference) {
            "only_output" -> AzooKeyStyleLearningType.OnlyOutput
            "nothing" -> AzooKeyStyleLearningType.Nothing
            else -> AzooKeyStyleLearningType.InputAndOutput
        }
    }
}
