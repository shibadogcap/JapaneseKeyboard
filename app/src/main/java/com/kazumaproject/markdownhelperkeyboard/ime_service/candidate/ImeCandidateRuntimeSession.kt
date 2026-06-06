package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest

/**
 * セッション中に変化する IME 状態。[ImePreferencesSnapshot] とは別に毎リクエストで渡す。
 */
data class ImeCandidateRuntimeSession(
    val isPrivateMode: Boolean,
    val suppressSuggestions: Boolean,
    val isCandidateSelectionActive: Boolean,
    val isConverting: Boolean,
    val isDirectInputMode: Boolean,
    val qwertyMode: com.kazumaproject.core.domain.state.TenKeyQWERTYMode,
    val currentQwertyRomajiMode: Boolean,
    val onNormalBunsetsuResult: (request: CandidateRequest, result: BunsetsuCandidateResult) -> Unit = { _, _ -> },
)