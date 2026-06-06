package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

data class ImeCandidateSuggestResult(
    val candidates: List<Candidate>,
    val bunsetsuResult: BunsetsuCandidateResult?,
    val zenzRerankPlan: ImeCandidateZenzRerankPlan? = null,
    val emitAsyncZenzGeneration: Boolean = false,
    val emitAsyncZenzai: Boolean = false,
)