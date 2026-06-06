package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestPrivacy

/**
 * IME セッション由来の runtime フラグ。AzooKey 本家では [ComposingText] の状態と
 * キーボード側 policy で暗黙に決まる部分を明示化する。
 */
data class ConvertRuntimeContext(
    val privacy: CandidateRequestPrivacy = CandidateRequestPrivacy(),
    val isCandidateSelectionActive: Boolean = false,
    val isConverting: Boolean = false,
    val isDirectInputMode: Boolean = false,
    val liveConversionMode: AzooKeyLiveConversionMode = AzooKeyLiveConversionMode.Disabled,
    val learningTypeOverride: AzooKeyStyleLearningType? = null,
    val zenzaiModeOverride: AzooKeyStyleZenzaiMode? = null,
    val experimentalZenzaiPredictiveInput: Boolean = false,
)