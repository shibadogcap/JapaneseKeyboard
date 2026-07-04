package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode

/**
 * [ComposingText] + [ConvertRequestOptions] を内部 [CandidateRequest] へ写像する。
 * 既存パイプラインとの互換用ブリッジ。新規コードは [KanaKanjiConverter] を優先する。
 */
object CandidateRequestBridge {
    fun toCandidateRequest(
        composingText: ComposingText,
        options: ConvertRequestOptions,
        runtime: ConvertRuntimeContext,
        mode: CandidateRequestMode,
    ): CandidateRequest {
        val effectiveInput = if (composingText.convertTargetCursorPosition < composingText.convertTarget.length) {
            composingText.prefixToCursorPosition().convertTarget
        } else {
            composingText.convertTarget
        }
        return CandidateRequest(
            input = effectiveInput,
            mode = mode,
            nBest = options.nBest,
            useUserDictionary = options.useUserDictionary,
            useUserTemplate = options.useUserTemplate,
            useRomajiCandidates = options.useRomajiCandidates,
            useBunsetsu = options.useBunsetsu,
            useOmissionSearch = options.useOmissionSearch,
            japanesePredictionMode = options.requireJapanesePrediction,
            englishPredictionMode = options.requireEnglishPrediction,
            learningType = runtime.learningTypeOverride ?: options.learningType,
            zenzaiMode = runtime.zenzaiModeOverride ?: options.zenzaiMode,
            experimentalZenzaiPredictiveInput = options.experimentalZenzaiPredictiveInput ||
                runtime.experimentalZenzaiPredictiveInput,
            liveConversionMode = runtime.liveConversionMode,
            typoCorrectionMode = options.typoCorrectionMode,
            privacy = runtime.privacy,
            specialCandidateProviders = options.specialCandidateProviders,
            versionString = options.metadata?.versionString,
            isCandidateSelectionActive = runtime.isCandidateSelectionActive,
            isConverting = runtime.isConverting,
            isDirectInputMode = runtime.isDirectInputMode,
            previousInput = runtime.previousInput,
            previousLatticeNodes = runtime.previousLatticeNodes,
            completedCandidate = runtime.completedCandidate,
            composingText = runtime.composingText ?: composingText,
            previousComposingText = runtime.previousComposingText,
        )
    }
}