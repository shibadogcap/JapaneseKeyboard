package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana

/**
 * Android-side equivalent of azooKey's ConversionResult mixing stage.
 *
 * Conversion, prediction, English prediction, and top-level special candidates
 * stay source-specific until this policy decides what belongs in the visible
 * candidate row.
 */
object AzooKeyStyleCandidateMixer {

    private val FULL_TO_HALF_MAP = mapOf(
        '（' to '(', '）' to ')',
        '「' to '[', '」' to ']',
        '『' to '[', '』' to ']',
        '｛' to '{', '｝' to '}',
        '［' to '[', '］' to ']',
        '＜' to '<', '＞' to '>',
        '【' to '[', '】' to ']',
        '〔' to '(', '〕' to ')',
        '〘' to '[', '〙' to ']',
        '〚' to '[', '〛' to ']',
        '〈' to '<', '〉' to '>',
        '《' to '<', '》' to '>',
        '«' to '<', '»' to '>',
        '‹' to '<', '›' to '>',
        '＋' to '+', 'ー' to '-', '＊' to '*', '＝' to '=',
        '・' to '·', '！' to '!', '＃' to '#', '％' to '%',
        '＆' to '&', '＇' to '\'', '＂' to '"', '〜' to '~',
        '｜' to '|', '￡' to '£', '＄' to '$', '￥' to '¥',
        '＠' to '@', '｀' to '`', '；' to ';', '：' to ':',
        '，' to ',', '．' to '.', '＼' to '\\', '／' to '/',
        '＿' to '_', '　' to ' '
    )

    private fun Char.isSymbolOrBracket(): Boolean {
        if (this in '\u0020'..'\u002F' || this in '\u003A'..'\u0040' || this in '\u005B'..'\u0060' || this in '\u007B'..'\u007E') {
            return true
        }
        if (this in '\uFF01'..'\uFF0F' || this in '\uFF1A'..'\uFF20' || this in '\uFF3B'..'\uFF40' || this in '\uFF5B'..'\uFF5E') {
            return true
        }
        if (this in '\u3001'..'\u301C') {
            return true
        }
        if (this == '«' || this == '»' || this == '‹' || this == '›') {
            return true
        }
        return false
    }

    private fun String.containsSymbolOrBracket(): Boolean {
        return this.any { it.isSymbolOrBracket() }
    }

    private fun String.convertSymbolsAndBracketsToHalfWidth(): String {
        return this.map { ch ->
            FULL_TO_HALF_MAP[ch] ?: run {
                if (ch in '０'..'９') {
                    (ch.code - 0xFEE0).toChar()
                } else if (ch in 'Ａ'..'Ｚ' || ch in 'ａ'..'ｚ') {
                    (ch.code - 0xFEE0).toChar()
                } else {
                    ch
                }
            }
        }.joinToString("")
    }

    private fun getUniqueCandidates(
        candidates: List<Candidate>,
        seenCandidates: Set<String> = emptySet()
    ): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val textIndex = mutableMapOf<String, Int>()
        candidates.forEach { candidate ->
            if (candidate.string.isNotEmpty() && !seenCandidates.contains(candidate.string)) {
                val existingIndex = textIndex[candidate.string]
                if (existingIndex != null) {
                    val existing = result[existingIndex]
                    if (existing.value < candidate.value || existing.length < candidate.length) {
                        result[existingIndex] = candidate
                    }
                } else {
                    textIndex[candidate.string] = result.size
                    result.add(candidate)
                }
            }
        }
        return result
    }

    fun mix(
        mainCandidates: List<Candidate>,
        japanesePredictionCandidates: List<Candidate> = emptyList(),
        englishPredictionCandidates: List<Candidate> = emptyList(),
        specialCandidates: List<Candidate> = emptyList(),
        firstClauseCandidates: List<Candidate> = emptyList(),
        wordCandidates: List<Candidate> = emptyList(),
        options: AzooKeyStyleConvertRequestOptions = AzooKeyStyleConvertRequestOptions(),
        input: String? = null,
    ): AzooKeyStyleConversionResult {
        val separatedJapanesePredictions =
            if (options.japanesePredictionMode.isEnabled) japanesePredictionCandidates else emptyList()
        val separatedEnglishPredictions =
            if (options.englishPredictionMode.isEnabled) englishPredictionCandidates else emptyList()

        val wholeSentenceUniqueCandidates = getUniqueCandidates(mainCandidates)
        val bestFiveSentenceCandidates = if (options.zenzaiMode.isEnabled) {
            val first5 = wholeSentenceUniqueCandidates.take(5)
            val sortedValues = first5.map { it.value }.sortedDescending()
            first5.mapIndexed { index, candidate ->
                candidate.copy(value = sortedValues[index])
            }
        } else {
            wholeSentenceUniqueCandidates.sortedByDescending { it.value }.take(5)
        }

        val bestThreePredictionCandidates = if (options.japanesePredictionMode.shouldMix) {
            getUniqueCandidates(separatedJapanesePredictions).sortedByDescending { it.value }.take(3)
        } else {
            emptyList()
        }

        val foreignCandidates = if (options.englishPredictionMode.shouldMix) {
            getUniqueCandidates(separatedEnglishPredictions).sortedByDescending { it.value }.take(3)
        } else {
            emptyList()
        }

        val mixedCandidates = getUniqueCandidates(
            bestFiveSentenceCandidates + bestThreePredictionCandidates + foreignCandidates
        )
        val fullCandidates = mixedCandidates.sortedByDescending { it.value }.take(5)

        val seen = LinkedHashSet<String>()
        val visibleCandidates = buildList {
            addUnique(fullCandidates, seen)

            val sortedFirstClause = getUniqueCandidates(firstClauseCandidates)
                .filter { it.string !in seen }
                .sortedWith(
                    compareByDescending<Candidate> { it.length.toInt() }
                        .thenByDescending { it.value }
                )
                .take(FIRST_CLAUSE_VISIBLE_LIMIT)
            addUnique(sortedFirstClause, seen)

            val sortedWord = getUniqueCandidates(wordCandidates)
                .filter { it.string !in seen }
                .sortedWith(
                    compareByDescending<Candidate> { it.length.toInt() }
                        .thenByDescending { it.value }
                )
            addUnique(sortedWord, seen)

            addUnique(specialCandidates, seen)
        }

        return AzooKeyStyleConversionResult(
            mainResults = promoteExactReadingCandidate(
                candidates = visibleCandidates,
                mainCandidates = mainCandidates,
                input = input,
            ),
            predictionResults = separatedJapanesePredictions,
            englishPredictionResults = separatedEnglishPredictions,
            firstClauseResults = firstClauseCandidates,
        )
    }

    private fun MutableList<Candidate>.addUnique(
        candidates: List<Candidate>,
        seen: MutableSet<String>,
        generateHalfWidth: Boolean = true
    ) {
        candidates.forEach { candidate ->
            if (candidate.string.isNotEmpty() && seen.add(candidate.string)) {
                add(candidate)
                if (generateHalfWidth && candidate.string.containsSymbolOrBracket()) {
                    val halfWidthStr = candidate.string.convertSymbolsAndBracketsToHalfWidth()
                    if (halfWidthStr != candidate.string && seen.add(halfWidthStr)) {
                        add(candidate.copy(string = halfWidthStr, isLearningTarget = false))
                    }
                }
            }
        }
    }

    private fun promoteExactReadingCandidate(
        candidates: List<Candidate>,
        mainCandidates: List<Candidate>,
        input: String?,
    ): List<Candidate> {
        if (input.isNullOrEmpty()) return candidates
        val targetInputKatakana = input.hiraganaToKatakana()

        val list = candidates.toMutableList()
        val firstThreeHasExact = list.take(3).any { candidate ->
            candidate.yomi?.hiraganaToKatakana() == targetInputKatakana
        }
        if (firstThreeHasExact) {
            return list
        }

        var foundIndex = -1
        for (i in 3 until list.size) {
            if (list[i].yomi?.hiraganaToKatakana() == targetInputKatakana) {
                foundIndex = i
                break
            }
        }

        if (foundIndex != -1) {
            val candidate = list.removeAt(foundIndex)
            val insertIndex = minOf(list.size, EXACT_READING_TARGET_INDEX)
            list.add(insertIndex, candidate)
            return list
        }

        val extraCandidate = mainCandidates.firstOrNull { candidate ->
            candidate.yomi?.hiraganaToKatakana() == targetInputKatakana &&
            list.none { it.string == candidate.string }
        }
        if (extraCandidate != null) {
            val insertIndex = minOf(list.size, EXACT_READING_TARGET_INDEX)
            list.add(insertIndex, extraCandidate)
            return list
        }

        return list
    }

    private const val FIRST_CLAUSE_VISIBLE_LIMIT = 5
    private const val EXACT_READING_TARGET_INDEX = 2
}
