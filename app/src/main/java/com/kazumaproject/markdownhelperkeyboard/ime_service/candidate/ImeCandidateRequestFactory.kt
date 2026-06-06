package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.insertRoman2KanaAtCursor
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiComposingSnapshot
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRuntimeContext
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AuxiliaryCandidateSourceConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidatePostProcessEnvironment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ImeCandidateEnvironment

object ImeCandidateRequestFactory {
    fun buildConvertRequestOptions(
        preferences: ImeCandidatePreferences,
        mode: CandidateRequestMode,
    ): ConvertRequestOptions {
        val predictionMode = when (mode) {
            CandidateRequestMode.WithoutPrediction,
            CandidateRequestMode.EnglishKana -> AzooKeyStylePredictionMode.Disabled
            CandidateRequestMode.Normal,
            CandidateRequestMode.Original -> AzooKeyStylePredictionMode.AutoMix
        }
        return ConvertRequestOptions(
            nBest = preferences.nBest,
            requireJapanesePrediction = predictionMode,
            requireEnglishPrediction = AzooKeyStylePredictionMode.Disabled,
            learningType = preferences.learningType,
            zenzaiMode = preferences.zenzaiMode,
            experimentalZenzaiPredictiveInput = false,
            typoCorrectionMode = preferences.typoCorrectionMode,
            metadata = preferences.versionString?.let {
                ConvertRequestOptions.Metadata(versionString = it)
            },
            useUserDictionary = preferences.useUserDictionary,
            useUserTemplate = preferences.useUserTemplate,
            useRomajiCandidates = preferences.useRomajiCandidates,
            useBunsetsu = preferences.useBunsetsu,
            useOmissionSearch = preferences.useOmissionSearch,
        )
    }

    fun buildRuntimeContext(preferences: ImeCandidatePreferences): ConvertRuntimeContext {
        return ConvertRuntimeContext(
            privacy = preferences.privacy,
            isCandidateSelectionActive = preferences.isCandidateSelectionActive,
            isConverting = preferences.isConverting,
            isDirectInputMode = preferences.isDirectInputMode,
            liveConversionMode = preferences.liveConversionMode,
        )
    }

    fun buildEnvironment(
        preferences: ImeCandidatePreferences,
        conversionSession: ConversionSession? = null,
    ): ImeCandidateEnvironment {
        return ImeCandidateEnvironment(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = preferences.learnedPrefixMatchThreshold,
                userDictionaryPrefixMatchThreshold = preferences.userDictionaryPrefixMatchThreshold,
            ),
            systemEngineConfig = preferences.systemEngineConfig,
            isLearnDictionaryMode = preferences.isLearnDictionaryMode,
            romanize = preferences.romanize,
            toHankakuAlphabet = preferences.toHankakuAlphabet,
            onNormalBunsetsuResult = preferences.onNormalBunsetsuResult,
            latticeIncrementalState = conversionSession?.latticeIncrementalState,
        )
    }

    fun buildPostProcessEnvironment(
        preferences: ImeCandidatePreferences,
        hasNgWords: Boolean,
    ): CandidatePostProcessEnvironment {
        return CandidatePostProcessEnvironment(
            isNgWordEnabled = preferences.isNgWordFilterEnabled && hasNgWords,
            ngWordPattern = preferences.ngWordPattern,
            isOrderOverrideEnabled = preferences.isOrderOverrideEnabled,
        )
    }

    fun composingText(input: String): ComposingText = ComposingText.fromConvertTarget(input)

    /**
     * QWERTY / 物理キーボードのローマ字入力を Roman2Kana セグメント付き [ComposingText] にする。
     * スナップショットと表示文字列が一致しない場合は [composingText] にフォールバックする。
     */
    fun composingText(
        displayInput: String,
        qwerty: RomajiComposingSnapshot?,
        roman2Kana: AzooKeyRoman2KanaTransducer,
    ): ComposingText {
        if (qwerty == null) {
            return composingText(displayInput)
        }
        if (qwerty.displayText != displayInput) {
            return composingText(displayInput)
        }
        if (qwerty.pendingRomaji.isEmpty()) {
            return composingText(displayInput)
        }
        var text = composingText(qwerty.committedSurface)
        text = text.insertRoman2KanaAtCursor(qwerty.pendingRomaji, roman2Kana)
        return text
    }
}