package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.SpecialCandidateProvider

/**
 * AzooKey [ConvertRequestOptions](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 * Android 固有の repository 接続は [ConvertRuntimeContext] と [ImeCandidateEnvironment] 側で扱う。
 */
data class ConvertRequestOptions(
    val nBest: Int = 10,
    val requireJapanesePrediction: AzooKeyStylePredictionMode = AzooKeyStylePredictionMode.AutoMix,
    val requireEnglishPrediction: AzooKeyStylePredictionMode = AzooKeyStylePredictionMode.Disabled,
    val learningType: AzooKeyStyleLearningType = AzooKeyStyleLearningType.OnlyOutput,
    val zenzaiMode: AzooKeyStyleZenzaiMode = AzooKeyStyleZenzaiMode.Off,
    val experimentalZenzaiPredictiveInput: Boolean = false,
    val typoCorrectionMode: AzooKeyStyleTypoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
    val specialCandidateProviders: List<SpecialCandidateProvider> =
        com.kazumaproject.markdownhelperkeyboard.converter.candidate.DefaultSpecialCandidateProviders.providers,
    val metadata: Metadata? = null,
    val useUserDictionary: Boolean = true,
    val useUserTemplate: Boolean = true,
    val useRomajiCandidates: Boolean = true,
    val useBunsetsu: Boolean = false,
    val useOmissionSearch: Boolean = false,
) {
    data class Metadata(
        val versionString: String,
    )

    fun toAzooKeyStyleOptions(): AzooKeyStyleConvertRequestOptions {
        return AzooKeyStyleConvertRequestOptions(
            nBest = nBest,
            japanesePredictionMode = requireJapanesePrediction,
            englishPredictionMode = requireEnglishPrediction,
            learningType = learningType,
            zenzaiMode = zenzaiMode,
            typoCorrectionMode = typoCorrectionMode,
            specialCandidateProviders = specialCandidateProviders,
            versionString = metadata?.versionString,
        )
    }
}