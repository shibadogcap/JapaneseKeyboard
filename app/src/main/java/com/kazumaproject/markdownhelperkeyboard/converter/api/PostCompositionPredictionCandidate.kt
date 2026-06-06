package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyPValue
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType

/**
 * AzooKey 確定後予測候補。現段階では [Candidate] からの射影を提供する。
 */
data class PostCompositionPredictionCandidate(
    val text: String,
    val value: AzooKeyPValue,
    val source: PostCompositionPredictionSource = PostCompositionPredictionSource.SystemPrediction,
    val candidateType: Byte = source.defaultCandidateType,
    val isTerminal: Boolean = text in TERMINAL_MARKERS,
) {
    fun toCandidate(): Candidate {
        return Candidate(
            string = text,
            type = candidateType,
            length = text.length.toUByte(),
            score = value.toInt(),
            value = value,
        )
    }

    companion object {
        private val TERMINAL_MARKERS = setOf("。", ".", "．")

        fun fromCandidate(candidate: Candidate): PostCompositionPredictionCandidate {
            return PostCompositionPredictionCandidate(
                text = candidate.string,
                value = candidate.value,
                candidateType = candidate.type,
                source = PostCompositionPredictionSource.fromCandidateType(candidate.type),
            )
        }
    }
}

enum class PostCompositionPredictionSource(
    val defaultCandidateType: Byte,
) {
    LearnedTransition(CandidateType.LEARNED_HISTORY),
    SystemPrediction(CandidateType.POST_COMMIT_PREDICTION),
    ZeroHint(CandidateType.ZERO_HINT_PREDICTION),
    EmojiSuffix(CandidateType.EMOJI_SUFFIX);

    companion object {
        fun fromCandidateType(type: Byte): PostCompositionPredictionSource {
            return when (type) {
                CandidateType.LEARNED_HISTORY -> LearnedTransition
                CandidateType.ZERO_HINT_PREDICTION -> ZeroHint
                CandidateType.EMOJI_SUFFIX,
                CandidateType.EMOJI_SPECIAL -> EmojiSuffix
                else -> SystemPrediction
            }
        }
    }
}
