package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy

/**
 * Immutable snapshot of IME session fields required to build [CandidateRequest] and
 * converter environments. Populated from [com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService]
 * on each suggestion request.
 */
data class ImeCandidatePreferences(
    val nBest: Int,
    val useUserDictionary: Boolean,
    val useUserTemplate: Boolean,
    val useRomajiCandidates: Boolean,
    val useBunsetsu: Boolean,
    val useOmissionSearch: Boolean,
    val learningType: AzooKeyStyleLearningType,
    val zenzaiMode: AzooKeyStyleZenzaiMode,
    val liveConversionMode: AzooKeyLiveConversionMode,
    val typoCorrectionMode: AzooKeyStyleTypoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
    val privacy: CandidateRequestPrivacy,
    val versionString: String?,
    val learnedPrefixMatchThreshold: Int,
    val userDictionaryPrefixMatchThreshold: Int,
    val isLearnDictionaryMode: Boolean,
    val romanize: (String) -> String?,
    val toHankakuAlphabet: (String) -> String,
    val onNormalBunsetsuResult: (request: CandidateRequest, result: BunsetsuCandidateResult) -> Unit,
    val isNgWordFilterEnabled: Boolean,
    val ngWords: List<String>,
    val ngWordPattern: Regex,
    val isOrderOverrideEnabled: Boolean,
    val zenzProfile: String = "",
    val zenzTopic: String = "",
    val zenzStyle: String = "",
    val zenzPreference: String = "",
    val zenzRightSideContext: String = "",
    val isCandidateSelectionActive: Boolean = false,
    val isConverting: Boolean = false,
    val isDirectInputMode: Boolean = false,
    val englishCandidateInRoman2KanaInput: Boolean = false,
    val zenzaiInferenceLimit: Int = 1,
    val maxMemoryCount: Int = 65536,
    val keyboardLanguage: com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions.KeyboardLanguage =
        com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions.KeyboardLanguage.JaJp,
)