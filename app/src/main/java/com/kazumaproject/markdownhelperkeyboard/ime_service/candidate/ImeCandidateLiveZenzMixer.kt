package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleCandidateMixer
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate

/**
 * ライブ変換時の Zenz 候補と辞書候補の合流（本家 mixer 経路）。
 */
object ImeCandidateLiveZenzMixer {
    fun mergeIfApplicable(
        insertReading: String,
        dictionaryCandidates: List<Candidate>,
        zenzCandidates: List<ZenzCandidate>,
        options: AzooKeyStyleConvertRequestOptions = ImeCandidateZenzContext.LIVE_MIX_OPTIONS,
    ): List<Candidate>? {
        if (zenzCandidates.isEmpty()) return null
        if (zenzCandidates.first().originalString != insertReading) return null
        if (dictionaryCandidates.isEmpty()) return null
        if (dictionaryCandidates.first().length.toInt() != insertReading.length) return null

        val zenzAsCandidates = zenzCandidates.map {
            Candidate(
                string = it.string,
                type = it.type,
                length = it.length,
                score = it.score,
            )
        }
        val learned = dictionaryCandidates.filter { it.type.toInt() == 34 }
        val others = dictionaryCandidates.filter { it.type.toInt() != 34 }
        return AzooKeyStyleCandidateMixer.mix(
            mainCandidates = zenzAsCandidates + others,
            japanesePredictionCandidates = learned,
            options = options,
            input = insertReading,
        ).mainResults
    }
}