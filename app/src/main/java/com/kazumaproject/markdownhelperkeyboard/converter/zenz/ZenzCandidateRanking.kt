package com.kazumaproject.markdownhelperkeyboard.converter.zenz

import com.kazumaproject.core.domain.extensions.duplicateCharCount
import com.kazumaproject.core.domain.extensions.kanjiCount
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate

internal fun ZenzCandidate.rank(prefix: String): Int {
    val prefixScore = commonPrefixLength(string, prefix) * 10
    val kanjiScore = string.kanjiCount() * 3
    val duplicatePenalty = string.duplicateCharCount() * 50
    val typeBonus = if (type == CandidateType.ZENZ_SPECIAL) 2 else 0
    return prefixScore + kanjiScore + typeBonus - duplicatePenalty
}

private fun commonPrefixLength(a: String, b: String): Int {
    val limit = minOf(a.length, b.length)
    for (i in 0 until limit) {
        if (a[i] != b[i]) return i
    }
    return limit
}