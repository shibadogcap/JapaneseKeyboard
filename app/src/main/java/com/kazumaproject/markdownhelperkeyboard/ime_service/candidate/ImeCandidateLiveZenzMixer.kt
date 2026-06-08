package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleCandidateMixer
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate

/**
 * ライブ変換時の Zenz 候補と辞書候補の合流（本家 mixer 経路）。
 *
 * AzooKey の設計に準拠し、Zenz 候補を辞書候補と「スコアでインターリーブ」する。
 * 辞書の第一候補スコアと Zenz スコアを比較して最もらしい候補が先頭に来る。
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

        // 学習履歴（type=34）は prediction レーンに分離して AzooKeyStyleCandidateMixer へ渡す
        val learned = dictionaryCandidates.filter { it.type.toInt() == 34 }
        // 変換候補（type != 34）は Neural（Zenz）候補とスコアでインターリーブ
        val others = dictionaryCandidates.filter { it.type.toInt() != 34 }

        // AzooKey 準拠: Zenz 候補の score（2000）と辞書候補の score を比べてソート。
        // Zenz の score 2000 は辞書スコアの典型的な範囲（~1000–3000）と同じスケールなので
        // そのままマージしてスコア降順で並べると最もらしい候補が先頭になる。
        val merged = (zenzAsCandidates + others)
            .sortedWith(
                compareByDescending<Candidate> {
                    // Neural レーン（Zenz）を辞書 NBEST と同列で扱う
                    it.value
                }.thenByDescending {
                    // 同スコアなら Zenz > NBEST で Neural を優先
                    val lane = CandidateType.laneOf(it).ordinal
                    -lane // Neural.ordinal が小さい ⇒ 数値を逆転して Neural を前に
                }
            )

        return AzooKeyStyleCandidateMixer.mix(
            mainCandidates = merged,
            japanesePredictionCandidates = learned,
            options = options,
            input = insertReading,
        ).mainResults
    }
}