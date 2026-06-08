package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyStyleCandidateMixerTest {
    @Test
    fun mixAutoMixesJapanesePredictionIntoMainResults() {
        val result = AzooKeyStyleCandidateMixer.mix(
            mainCandidates = listOf(candidate("変換", CandidateType.NBEST, 10, value = -10f)),
            japanesePredictionCandidates = listOf(
                candidate("予測1", CandidateType.LEARNED_HISTORY, 20, value = -1f),
                candidate("予測2", CandidateType.LEARNED_HISTORY, 30, value = -2f),
                candidate("予測3", CandidateType.LEARNED_HISTORY, 40, value = -3f),
                candidate("予測4", CandidateType.LEARNED_HISTORY, 50, value = -4f),
            ),
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
                englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            )
        )

        assertEquals(listOf("予測1", "予測2", "予測3", "変換"), result.mainResults.map { it.string })
        assertEquals(listOf("予測1", "予測2", "予測3", "予測4"), result.predictionResults.map { it.string })
    }

    @Test
    fun mixManualMixKeepsPredictionSeparated() {
        val result = AzooKeyStyleCandidateMixer.mix(
            mainCandidates = listOf(candidate("変換", CandidateType.NBEST, 10)),
            japanesePredictionCandidates = listOf(candidate("予測", CandidateType.LEARNED_HISTORY, 20)),
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.ManualMix,
            )
        )

        assertEquals(listOf("変換"), result.mainResults.map { it.string })
        assertEquals(listOf("予測"), result.predictionResults.map { it.string })
    }

    @Test
    fun mixDisabledDropsSeparatedPredictions() {
        val result = AzooKeyStyleCandidateMixer.mix(
            mainCandidates = listOf(candidate("変換", CandidateType.NBEST, 10)),
            japanesePredictionCandidates = listOf(candidate("予測", CandidateType.LEARNED_HISTORY, 20)),
            englishPredictionCandidates = listOf(candidate("hello", CandidateType.ENGLISH, 30)),
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
                englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            )
        )

        assertEquals(listOf("変換"), result.mainResults.map { it.string })
        assertEquals(emptyList<String>(), result.predictionResults.map { it.string })
        assertEquals(emptyList<String>(), result.englishPredictionResults.map { it.string })
    }

    @Test
    fun mixKeepsAzooKeyProcessResultOrderAndAppendsWordCandidates() {
        val result = AzooKeyStyleCandidateMixer.mix(
            mainCandidates = listOf(
                candidate("全文1", CandidateType.NBEST, 10),
                candidate("全文2", CandidateType.NBEST, 9),
                candidate("全文3", CandidateType.NBEST, 8),
                candidate("全文4", CandidateType.NBEST, 7),
                candidate("全文5", CandidateType.NBEST, 6),
                candidate("全文6", CandidateType.NBEST, 5),
            ),
            firstClauseCandidates = listOf(candidate("文節", CandidateType.PART_OF_LETTERS, 20)),
            wordCandidates = listOf(
                candidate("短", CandidateType.PART_OF_LETTERS, 1),
                candidate("長い単語", CandidateType.PART_OF_LETTERS, 1),
            ),
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            )
        )

        assertEquals(
             listOf("全文1", "全文2", "全文3", "全文4", "全文5", "文節", "長い単語", "短"),
             result.mainResults.map { it.string },
         )
    }

    @Test
    fun mixPromotesExactReadingCandidateIntoTopThree() {
        val result = AzooKeyStyleCandidateMixer.mix(
            mainCandidates = listOf(
                candidate("候補1", CandidateType.NBEST, 10, value = 10f),
                candidate("候補2", CandidateType.NBEST, 9, value = 9f),
                candidate("候補3", CandidateType.NBEST, 8, value = 8f),
                candidate("あずーきー", CandidateType.HIRAGANA, 1, value = 1f, yomi = "あずーきー"),
            ),
            input = "あずーきー",
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
            )
        )

        assertEquals("あずーきー", result.mainResults[2].string)
    }

    @Test
    fun mixPromotesExactReadingCandidateBasedOnYomi() {
        val result = AzooKeyStyleCandidateMixer.mix(
            mainCandidates = listOf(
                candidate("候補1", CandidateType.NBEST, 10, value = 10f),
                candidate("候補2", CandidateType.NBEST, 9, value = 9f),
                candidate("候補3", CandidateType.NBEST, 8, value = 8f),
                candidate("今日", CandidateType.NBEST, 1, value = 1f, yomi = "きょう"),
            ),
            input = "きょう",
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
            )
        )

        assertEquals("今日", result.mainResults[2].string)
    }

    @Test
    fun mixGeneratesHalfWidthForSymbolsAndBrackets() {
        val result = AzooKeyStyleCandidateMixer.mix(
            mainCandidates = listOf(
                candidate("「", CandidateType.NBEST, 10, value = 10f),
            ),
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
            )
        )

        assertEquals(listOf("「", "["), result.mainResults.map { it.string })
    }

    @Test
    fun mixAppendsSpecialCandidatesAfterWordCandidates() {
        val result = AzooKeyStyleCandidateMixer.mix(
            mainCandidates = listOf(candidate("変換", CandidateType.NBEST, 10)),
            firstClauseCandidates = listOf(candidate("文節", CandidateType.PART_OF_LETTERS, 20)),
            wordCandidates = listOf(candidate("単語", CandidateType.PART_OF_LETTERS, 30)),
            specialCandidates = listOf(candidate("★", CandidateType.SPECIAL, 40)),
            options = AzooKeyStyleConvertRequestOptions(
                japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
            )
        )

        assertEquals(listOf("変換", "文節", "単語", "★"), result.mainResults.map { it.string })
    }

    private fun candidate(
        string: String,
        type: Byte,
        score: Int,
        value: AzooKeyPValue = score.toFloat(),
        yomi: String? = null,
    ): Candidate {
        return Candidate(
            string = string,
            type = type,
            length = string.length.toUByte(),
            score = score,
            value = value,
            yomi = yomi,
        )
    }
}
