package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Hilt-injectable boundary for AzooKey-style candidate conversion.
 *
 * [AzooKeyStyleCandidateService] remains the low-level orchestrator; this interface is the
 * IME-facing contract described in [docs/azookey-candidate-service-boundary.md].
 */
interface CandidateService {
    suspend fun convert(
        request: CandidateRequest,
        environment: ImeCandidateEnvironment,
    ): AzooKeyStyleCandidateServiceResult

    suspend fun postProcess(
        input: String,
        candidates: List<Candidate>,
        environment: CandidatePostProcessEnvironment,
    ): List<Candidate>
}