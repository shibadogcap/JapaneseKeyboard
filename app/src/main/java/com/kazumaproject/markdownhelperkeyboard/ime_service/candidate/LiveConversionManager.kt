package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.adjustCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.makePrefixClauseCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyJapaneseConversionText

class LiveConversionManager(var enabled: Boolean) {

    var isFirstClauseCompletion: Boolean = false
        private set

    var lastUsedCandidate: Candidate? = null
        private set

    private val headClauseCandidateHistories = mutableListOf<MutableList<Candidate>>()

    fun stopComposition() {
        this.lastUsedCandidate = null
        this.headClauseCandidateHistories.clear()
    }

    fun updateAfterFirstClauseCompletion() {
        this.lastUsedCandidate = null
        this.isFirstClauseCompletion = false
        if (headClauseCandidateHistories.isNotEmpty()) {
            headClauseCandidateHistories.removeAt(0)
        }
    }

    private fun updateHistories(newCandidate: Candidate, firstClauseCandidates: List<Candidate>) {
        var data = newCandidate.data
        var count = 0
        while (data.isNotEmpty()) {
            var clause = makePrefixClauseCandidate(data)
            if (count == 0) {
                val first = firstClauseCandidates.firstOrNull { it.string == clause.string }
                if (first != null) {
                    clause = clause.copy(length = first.length)
                }
            }
            if (this.headClauseCandidateHistories.size <= count) {
                this.headClauseCandidateHistories.add(mutableListOf(clause))
            } else {
                this.headClauseCandidateHistories[count].add(clause)
            }
            data = data.drop(clause.data.size)
            count++
        }
    }

    fun updateWithNewResults(
        composingText: ComposingText,
        candidates: List<Candidate>,
        firstClauseResults: List<Candidate>,
        convertTargetCursorPosition: Int,
        convertTarget: String
    ): String {
        var candidate: Candidate
        if (convertTargetCursorPosition > 1) {
            val matched = candidates.firstOrNull {
                !AzooKeyJapaneseConversionText.containsHangul(it.string) &&
                    it.coversFullConvertTarget(convertTarget.length)
            }
            candidate = matched ?: fallbackCandidate(convertTarget)
        } else {
            candidate = fallbackCandidate(convertTarget)
        }

        val adjusted = candidate.adjustCandidate()

        if (convertTargetCursorPosition > 0) {
            this.setLastUsedCandidate(adjusted, firstClauseResults)
            return adjusted.string
        } else {
            this.setLastUsedCandidate(null)
            return ""
        }
    }

    private fun Candidate.coversFullConvertTarget(convertTargetLength: Int): Boolean {
        if (data.isNotEmpty()) {
            return data.sumOf { it.reading.length } == convertTargetLength
        }
        return effectiveRubyCount == convertTargetLength
    }

    fun setLastUsedCandidate(candidate: Candidate?, firstClauseCandidates: List<Candidate> = emptyList()) {
        if (candidate != null) {
            val lastUsed = this.lastUsedCandidate
            val diff = if (lastUsed != null) {
                val lastLength = lastUsed.data.sumOf { it.reading.length }
                val newLength = candidate.data.sumOf { it.reading.length }
                newLength - lastLength
            } else {
                1
            }
            this.lastUsedCandidate = candidate
            if (diff > 0) {
                this.updateHistories(candidate, firstClauseCandidates)
            } else if (diff < 0) {
                for (hist in this.headClauseCandidateHistories) {
                    if (hist.isNotEmpty()) {
                        hist.removeAt(hist.size - 1)
                    }
                }
            } else {
                for (hist in this.headClauseCandidateHistories) {
                    if (hist.isNotEmpty()) {
                        hist.removeAt(hist.size - 1)
                    }
                }
                this.updateHistories(candidate, firstClauseCandidates)
            }
        } else {
            this.lastUsedCandidate = null
            this.headClauseCandidateHistories.clear()
        }
    }

    fun candidateForCompleteFirstClause(threshold: Int = 3): Candidate? {
        val history = headClauseCandidateHistories.firstOrNull() ?: return null
        if (history.size < threshold) {
            return null
        }
        val suffix = history.takeLast(threshold)
        val texts = suffix.map { it.string }.toSet()
        if (texts.size == 1) {
            this.isFirstClauseCompletion = true
            return history.last()
        }
        return null
    }

    private fun fallbackCandidate(convertTarget: String): Candidate {
        val fallbackEntry = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry(
            surface = convertTarget,
            reading = convertTarget.hiraganaToKatakana(),
            leftId = 2,
            rightId = 2,
            mid = 501,
            wordCost = 0,
            sourceKind = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind.System,
        )
        return Candidate(
            string = convertTarget,
            type = 3.toByte(),
            length = convertTarget.length.toUByte(),
            score = 0,
            value = 0f,
            yomi = convertTarget,
            data = listOf(fallbackEntry),
        )
    }
}
