package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.CandidateData
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.ClauseDataUnit

/**
 * AzooKey [KanaKanjiConverter.getPredictionCandidate](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
object AzooKeyPredictionHelper {

    suspend fun getPredictionCandidate(
        bestCandidateDataForPrediction: CandidateData,
        composingText: ComposingText,
        kana2Kanji: AzooKeyKana2Kanji,
        useMemory: Boolean,
        roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): List<Candidate> {
        val candidates = mutableListOf<Candidate>()
        var prepart = bestCandidateDataForPrediction
        var lastPart: Pair<ClauseDataUnit, Float>? = null
        var iterationCount = 0

        while (iterationCount < 2 && prepart.clauses.isNotEmpty()) {
            if (lastPart != null) {
                val clauses = prepart.clauses.toMutableList()
                if (clauses.isEmpty()) break
                val lastUnit = clauses.removeAt(clauses.lastIndex)
                val mergedClause = lastUnit.first.copy(
                    text = lastUnit.first.text + lastPart.first.text,
                    reading = lastUnit.first.reading + lastPart.first.reading,
                    ranges = (lastUnit.first.ranges + lastPart.first.ranges).toMutableList(),
                )
                val newLastPart = mergedClause to (lastUnit.second + lastPart.second)
                prepart = CandidateData(clauses = clauses, data = prepart.data)
                val predictions = kana2Kanji.getPredictionCandidates(
                    composingText = composingText,
                    prepart = prepart,
                    lastClause = newLastPart.first,
                    nBest = 5,
                    useMemory = useMemory,
                    roman2Kana = roman2Kana,
                )
                lastPart = newLastPart
                if (predictions.isNotEmpty()) {
                    candidates += predictions
                    iterationCount++
                }
            } else {
                val clauses = prepart.clauses.toMutableList()
                if (clauses.isEmpty()) break
                lastPart = clauses.removeAt(clauses.lastIndex)
                prepart = CandidateData(clauses = clauses, data = prepart.data)
                val predictions = kana2Kanji.getPredictionCandidates(
                    composingText = composingText,
                    prepart = prepart,
                    lastClause = lastPart.first,
                    nBest = 5,
                    useMemory = useMemory,
                    roman2Kana = roman2Kana,
                )
                if (predictions.isNotEmpty()) {
                    candidates += predictions
                    iterationCount++
                }
            }
        }

        if (prepart.clauses.isNotEmpty() && lastPart != null) {
            var fullClause = prepart.clauses.first().first.copy(
                ranges = prepart.clauses.first().first.ranges.toMutableList(),
            )
            for (unit in prepart.clauses.drop(1)) {
                fullClause = fullClause.copy(
                    text = fullClause.text + unit.first.text,
                    reading = fullClause.reading + unit.first.reading,
                    ranges = (fullClause.ranges + unit.first.ranges).toMutableList(),
                )
            }
            fullClause = fullClause.copy(
                text = fullClause.text + lastPart.first.text,
                reading = fullClause.reading + lastPart.first.reading,
                ranges = (fullClause.ranges + lastPart.first.ranges).toMutableList(),
            )
            val emptyPrepart = CandidateData(clauses = emptyList(), data = emptyList())
            candidates += kana2Kanji.getPredictionCandidates(
                composingText = composingText,
                prepart = emptyPrepart,
                lastClause = fullClause,
                nBest = 5,
                useMemory = useMemory,
                roman2Kana = roman2Kana,
            )
        }

        return candidates
    }

    fun getForeignPredictionCandidates(
        inputData: ComposingText,
        language: String = "en-US",
        penalty: Float = -5f,
    ): List<Candidate> {
        if (language != "en-US") return emptyList()
        val ruby = inputData.convertTarget.filter { it.isLetter() }
        if (ruby.isEmpty() || !ruby.all { it.code in 65..90 || it.code in 97..122 }) {
            return emptyList()
        }
        val entryBase = { surface: String, reading: String, value: Float ->
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry(
                surface = surface,
                reading = reading,
                leftId = AzooKeyCid.PROPER_NOUN,
                rightId = AzooKeyCid.PROPER_NOUN,
                mid = AzooKeyMid.GENERAL,
                wordCost = 5,
                value = value,
                sourceKind = AzooKeyDictionarySourceKind.System,
            )
        }
        return listOf(
            Candidate(
                string = ruby,
                type = CandidateType.NBEST,
                length = ruby.length.toUByte(),
                score = penalty.toInt(),
                value = penalty,
                yomi = ruby.uppercase(),
                data = listOf(entryBase(ruby, ruby.uppercase(), penalty)),
            ),
        )
    }
}
