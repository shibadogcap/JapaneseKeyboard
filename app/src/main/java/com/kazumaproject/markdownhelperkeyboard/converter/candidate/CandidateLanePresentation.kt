package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * AzooKey iOS の mainResults / supplementaryCandidates 表示分離。
 * ライブ変換は main のみを参照し、絵文字・記号は supplementary に回す。
 */
object CandidateLanePresentation {
    private val supplementaryTypes: Set<Byte> = setOf(
        CandidateType.EMOJI_LEGACY,
        CandidateType.EMOTICON_LEGACY,
        CandidateType.SYMBOL_LEGACY,
        CandidateType.EMOJI_SPECIAL,
        CandidateType.EMOJI_SUFFIX,
        CandidateType.SYMBOL_SPECIAL,
    )

    fun isSupplementary(candidate: Candidate): Boolean {
        return candidate.type in supplementaryTypes ||
            CandidateType.laneOf(candidate) == CandidateLane.Special &&
            candidate.type in supplementaryTypes
    }

    fun splitMainAndSupplementary(
        mainResults: List<Candidate>,
    ): Pair<List<Candidate>, List<Candidate>> {
        val main = mutableListOf<Candidate>()
        val supplementary = mutableListOf<Candidate>()
        for (candidate in mainResults) {
            if (isSupplementary(candidate)) {
                supplementary += candidate
            } else {
                main += candidate
            }
        }
        return main to supplementary
    }

    /** 候補バー表示用: main + supplementary（順序は main 優先）。 */
    fun mergeForDisplay(main: List<Candidate>, supplementary: List<Candidate>): List<Candidate> {
        if (supplementary.isEmpty()) return main
        val seen = main.map { it.string }.toMutableSet()
        val merged = main.toMutableList()
        for (candidate in supplementary) {
            if (seen.add(candidate.string)) {
                merged += candidate
            }
        }
        return merged
    }

    /** ライブ変換適用対象（Special 絵文字を除外）。 */
    fun forLiveConversion(mainResults: List<Candidate>): List<Candidate> {
        return mainResults.filterNot { isSupplementary(it) }
    }
}
