package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class CandidateSources(
    /** @deprecated AzooKey 互換では [memory] を使用 */
    val learned: List<Candidate> = emptyList(),
    /** AzooKey memory identifier 相当（lattice / main へ合流） */
    val memory: List<Candidate> = emptyList(),
    val systemPrediction: List<Candidate> = emptyList(),
    val userTemplate: List<Candidate> = emptyList(),
    val userDictionary: List<Candidate> = emptyList(),
    val system: List<Candidate> = emptyList(),
    val romaji: List<Candidate> = emptyList(),
    val english: List<Candidate> = emptyList(),
    val special: List<Candidate> = emptyList(),
    val firstClause: List<Candidate> = emptyList(),
    val word: List<Candidate> = emptyList(),
) {
    /** Raw bundle order (privacy 未適用). 表示用は [CandidateAssembler] が request で memory を gate する。 */
    val mainCandidates: List<Candidate>
        get() = userTemplate + userDictionary + system + memory + learned + romaji

    fun toConversionResult(
        request: CandidateRequest,
    ): AzooKeyStyleConversionResult {
        return AzooKeyStyleConverter.convert(request = request, sources = this)
    }

    fun merge(other: CandidateSources): CandidateSources {
        return CandidateSources(
            learned = learned + other.learned,
            memory = memory + other.memory,
            systemPrediction = systemPrediction + other.systemPrediction,
            userTemplate = userTemplate + other.userTemplate,
            userDictionary = userDictionary + other.userDictionary,
            system = system + other.system,
            romaji = romaji + other.romaji,
            english = english + other.english,
            special = special + other.special,
            firstClause = firstClause + other.firstClause,
            word = word + other.word,
        )
    }

    companion object {
        fun fromSuggestionLists(
            learnedCandidates: List<Candidate>,
            userTemplateCandidates: List<Candidate>,
            userDictionaryCandidates: List<Candidate>,
            engineCandidates: List<Candidate>,
            romajiCandidates: List<Candidate>,
            specialCandidates: List<Candidate> = emptyList(),
        ): CandidateSources {
            return CandidateSources(
                memory = learnedCandidates,
                userTemplate = userTemplateCandidates,
                userDictionary = userDictionaryCandidates,
                system = engineCandidates,
                romaji = romajiCandidates,
                special = specialCandidates
            )
        }
    }
}
