package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Keeps candidate mixing rules out of the IME surface.
 *
 * azooKey's converter treats learning, prediction, special providers, and neural
 * conversion as request options. This ranker is the Android-side staging point
 * for that style: generated candidates can stay source-specific, then be merged
 * here with explicit policy.
 */
object AzooKeyStyleCandidateRanker {
    fun rank(candidates: List<Candidate>): List<Candidate> {
        val sorted = if (candidates.any { it.isLearnedHistoryCandidate() }) {
            candidates.sortedWith(
                compareByDescending<Candidate> { it.isLearnedHistoryCandidate() }
                    .thenByDescending { it.value }
            )
        } else {
            candidates.sortedByDescending { it.value }
        }
        return sorted.distinctBy { it.string }
    }

    /**
     * ライブ変換モード時に Special レーン（絵文字・記号等）の候補スコアを減点する。
     *
     * AzooKey iOS の設計では、ライブ変換に使う候補は KanaKanjiConverter の mainResults のみで、
     * 絵文字は補足候補 (supplementaryCandidates) として別途 UI に追加される。
     * Android 版ではひとつの候補リストに混在するため、ライブ変換時はスコアペナルティで
     * Special 候補をシステム候補の後ろに回す。これにより候補一覧には表示されつつ、
     * 自動適用（selectLiveConversionCandidate）では除外される二重構造となっている。
     *
     * @param liveConversionEnabled ライブ変換が有効な場合のみペナルティを適用する
     */
    /**
     * @deprecated supplementaryCandidates 分離により不要。互換のため no-op。
     */
    fun applyLiveConversionEmojiPenalty(
        candidates: List<Candidate>,
        liveConversionEnabled: Boolean,
    ): List<Candidate> = candidates

    private fun Candidate.isLearnedHistoryCandidate(): Boolean {
        return CandidateType.laneOf(this) == CandidateLane.Learned
    }

    /** ライブ変換時に Special 候補に適用するスコアペナルティ（value を引く量） */
    const val LIVE_CONVERSION_EMOJI_PENALTY = 1000f
}
