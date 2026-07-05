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
    /** 前回変換時の入力文字列（4 経路分岐用）。null の場合は新規構築（all-path）。 */
    val previousInput: String? = null,
    /** 前回変換時の lattice ノード（4 経路分岐再利用用）。 */
    val previousLatticeNodes: List<*>? = null,
    /** 文節確定直後の変換で使う確定語。 */
    val completedCandidate: com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate? = null,
    /** 現在の composing（Roman2Kana セグメント含む）。 */
    val composingText: ComposingText? = null,
    /** 前回変換時の composing（afterComplete / dual-index 用）。 */
    val previousComposingText: ComposingText? = null,
)