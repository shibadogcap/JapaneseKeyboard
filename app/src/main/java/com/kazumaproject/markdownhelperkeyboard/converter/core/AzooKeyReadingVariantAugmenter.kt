package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.toHalfWidthKana

/**
 * AzooKey の getAdditionalCandidate 相当。Live Zenz マージ等で脱落した読みバリアントを
 * 表示直前に補完する。
 */
internal object AzooKeyReadingVariantAugmenter {
    fun augment(
        candidates: List<Candidate>,
        reading: String,
        includeHalfWidthKana: Boolean,
    ): List<Candidate> {
        if (!includeHalfWidthKana || reading.isEmpty()) return candidates

        val katakana = reading.hiraganaToKatakana()
        if (katakana.isEmpty()) return candidates

        val result = candidates.toMutableList()
        val seen = candidates.map { it.string }.toMutableSet()

        appendVariant(
            result = result,
            seen = seen,
            surface = katakana,
            type = CandidateType.KATAKANA,
            value = -14f,
            yomi = katakana,
            rubyCount = reading.length,
        )
        appendVariant(
            result = result,
            seen = seen,
            surface = reading,
            type = CandidateType.HIRAGANA,
            value = -14.5f,
            yomi = katakana,
            rubyCount = reading.length,
        )
        appendVariant(
            result = result,
            seen = seen,
            surface = katakana.toHalfWidthKana(),
            type = CandidateType.NBEST,
            value = -15f,
            yomi = katakana,
            rubyCount = reading.length,
        )

        return result
    }

    private fun appendVariant(
        result: MutableList<Candidate>,
        seen: MutableSet<String>,
        surface: String,
        type: Byte,
        value: Float,
        yomi: String,
        rubyCount: Int,
    ) {
        if (surface.isEmpty() || surface in seen) return
        result += Candidate(
            string = surface,
            type = type,
            length = surface.length.toUByte(),
            score = value.toInt(),
            value = value,
            yomi = yomi,
            rubyCount = rubyCount,
        )
        seen += surface
    }
}
