package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.SystemKanaKanjiEngineSourceConfig
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

/**
 * [ImePreferencesSnapshot] + ランタイム状態から [ImeCandidatePreferences] を組み立てる。
 * AzooKey 本家の request options 相当を IME から切り離す。
 */
object ImeCandidatePreferencesBuilder {
    private const val MIN_API_CANDIDATE_N_BEST = 24

    fun build(
        snapshot: ImePreferencesSnapshot,
        runtime: ImeCandidateRuntimeSession,
        appPreference: AppPreference,
        ngWords: List<String>,
        ngWordPattern: Regex,
        romanize: (String) -> String?,
        toHankakuAlphabet: (String) -> String,
        zenzaiEnabled: Boolean,
    ): ImeCandidatePreferences {
        val enableTypoCorrectionJapaneseFlick =
            snapshot.enableTypoCorrectionJapaneseFlickKeyboardPreference &&
                (runtime.qwertyMode == TenKeyQWERTYMode.Default ||
                    runtime.qwertyMode == TenKeyQWERTYMode.Sumire)
        val enableTypoCorrectionQwertyEnglish =
            snapshot.enableTypoCorrectionQwertyEnglishKeyboardPreference &&
                (runtime.qwertyMode == TenKeyQWERTYMode.TenKeyQWERTY ||
                    (runtime.qwertyMode == TenKeyQWERTYMode.TenKeyQWERTYRomaji &&
                        !runtime.currentQwertyRomajiMode))

        return ImeCandidatePreferences(
            nBest = snapshot.nBest.coerceAtLeast(MIN_API_CANDIDATE_N_BEST),
            useUserDictionary = snapshot.isUserDictionaryEnable,
            useUserTemplate = snapshot.isUserTemplateEnable,
            useRomajiCandidates = snapshot.conversionCandidatesRomajiEnablePreference,
            useBunsetsu = snapshot.bunsetsuSeparation,
            useOmissionSearch = snapshot.isOmissionSearchEnable,
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
            systemEngineConfig = SystemKanaKanjiEngineSourceConfig(
                mozcUtPersonName = snapshot.mozcUTPersonName,
                mozcUTPlaces = snapshot.mozcUTPlaces,
                mozcUTWiki = snapshot.mozcUTWiki,
                mozcUTNeologd = snapshot.mozcUTNeologd,
                mozcUTWeb = snapshot.mozcUTWeb,
                enableTypoCorrectionJapaneseFlick = enableTypoCorrectionJapaneseFlick,
                enableTypoCorrectionQwertyEnglish = enableTypoCorrectionQwertyEnglish,
                typoCorrectionOffsetScore =
                    snapshot.enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference,
                omissionSearchOffsetScore = snapshot.omissionSearchOffsetScorePreference,
            ),
            isLearnDictionaryMode = snapshot.isLearnDictionaryMode,
            romanize = romanize,
            toHankakuAlphabet = toHankakuAlphabet,
            onNormalBunsetsuResult = runtime.onNormalBunsetsuResult,
            isNgWordFilterEnabled = snapshot.isNgWordEnable,
            ngWords = ngWords,
            ngWordPattern = ngWordPattern,
            isOrderOverrideEnabled = appPreference.candidate_order_override_enable_preference == true,
            isCandidateSelectionActive = runtime.isCandidateSelectionActive,
            isConverting = runtime.isConverting,
            isDirectInputMode = runtime.isDirectInputMode,
        )
    }

    /**
     * AzooKey [LearningType]: 変換中の memory 読み取りは [OnlyOutput]、書き込みは確定経路で行う。
     */
    fun learningTypeFromSnapshot(snapshot: ImePreferencesSnapshot): AzooKeyStyleLearningType {
        return if (snapshot.enablePredictionSearchLearnDictionaryPreference) {
            AzooKeyStyleLearningType.OnlyOutput
        } else {
            AzooKeyStyleLearningType.Nothing
        }
    }
}
