package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Names candidate type numbers that are shared across conversion, ranking, and UI.
 *
 * Many raw type numbers still exist in the codebase. Keep this registry small
 * and expand it only when a behavior needs a named contract.
 */
object CandidateType {
    const val NBEST: Byte = 1
    const val PART_OF_LETTERS: Byte = 2
    const val HIRAGANA: Byte = 3
    const val KATAKANA: Byte = 4
    const val EMOJI_LEGACY: Byte = 11
    const val EMOTICON_LEGACY: Byte = 12
    const val SYMBOL_LEGACY: Byte = 13
    const val USER_DICTIONARY: Byte = 28
    const val ENGLISH: Byte = 29
    const val ZENZ: Byte = 33
    const val LEARNED_HISTORY: Byte = 34
    const val TYPO_CORRECTION_QWERTY: Byte = 35
    const val ZENZ_CONTEXTUAL: Byte = 37
    const val ZENZ_SPECIAL: Byte = 40
    const val SPECIAL: Byte = 41
    const val UNICODE_SPECIAL: Byte = 42
    const val COMMA_SEPARATED_NUMBER_SPECIAL: Byte = 43
    const val TYPOGRAPHY_SPECIAL: Byte = 44
    const val EMOJI_SPECIAL: Byte = 45
    const val TIME_EXPRESSION_SPECIAL: Byte = 46
    const val CALENDAR_SPECIAL: Byte = 47
    const val EMAIL_ADDRESS_SPECIAL: Byte = 48
    const val SYMBOL_SPECIAL: Byte = 49
    const val VERSION_SPECIAL: Byte = 50
    const val POST_COMMIT_PREDICTION: Byte = 51
    const val ZERO_HINT_PREDICTION: Byte = 52
    const val EMOJI_SUFFIX: Byte = 53
    const val HALF_WIDTH_KATAKANA_SPECIAL: Byte = 54

    fun laneOf(candidate: Candidate): CandidateLane {
        return when (candidate.type) {
            LEARNED_HISTORY -> CandidateLane.Learned
            POST_COMMIT_PREDICTION,
            ZERO_HINT_PREDICTION -> CandidateLane.Prediction
            ZENZ,
            ZENZ_CONTEXTUAL,
            ZENZ_SPECIAL -> CandidateLane.Neural
            USER_DICTIONARY -> CandidateLane.UserDictionary
            HIRAGANA,
            KATAKANA,
            ENGLISH -> CandidateLane.Transform
            SPECIAL,
            UNICODE_SPECIAL,
            COMMA_SEPARATED_NUMBER_SPECIAL,
            TYPOGRAPHY_SPECIAL,
            EMOJI_SPECIAL,
            TIME_EXPRESSION_SPECIAL,
            CALENDAR_SPECIAL,
            EMAIL_ADDRESS_SPECIAL,
            SYMBOL_SPECIAL,
            VERSION_SPECIAL,
            EMOJI_SUFFIX,
            HALF_WIDTH_KATAKANA_SPECIAL,
            EMOJI_LEGACY,
            EMOTICON_LEGACY,
            SYMBOL_LEGACY -> CandidateLane.Special
            else -> CandidateLane.System
        }
    }
}

enum class CandidateLane {
    Learned,
    Prediction,
    Neural,
    UserDictionary,
    Transform,
    Special,
    System,
}
