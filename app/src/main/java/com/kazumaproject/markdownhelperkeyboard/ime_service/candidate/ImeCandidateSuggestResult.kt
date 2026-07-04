package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

data class ImeCandidateSuggestResult(
    /** 候補バー表示用（main + supplementary） */
    val candidates: List<Candidate>,
    /** ライブ変換・確定用（supplementary 除外） */
    val mainResults: List<Candidate> = candidates,
    val supplementaryCandidates: List<Candidate> = emptyList(),
    val predictionResults: List<Candidate> = emptyList(),
    val englishPredictionResults: List<Candidate> = emptyList(),
    val bunsetsuResult: BunsetsuCandidateResult?,
    val zenzRerankPlan: ImeCandidateZenzRerankPlan? = null,
    val emitAsyncZenzGeneration: Boolean = false,
    val emitAsyncZenzai: Boolean = false,
    val firstClauseResults: List<Candidate> = emptyList(),
)