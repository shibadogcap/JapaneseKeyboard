package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * Resolves Zenz constraint outputs to dictionary-backed candidate surfaces, matching AzooKey's
 * `wholeResult` / `fixResult` behavior (constraints for lattice search, not raw model text).
 */
internal object ZenzDictionaryCandidateResolver {
    fun resolveSurface(
        dictionaryCandidates: List<Candidate>,
        constraint: String,
        fallback: String,
    ): String {
        val normalized = constraint.trim()
        if (normalized.isEmpty()) {
            return fallback
        }
        return dictionaryCandidates
            .firstOrNull { it.string == normalized }
            ?.string
            ?: fallback
    }
}
