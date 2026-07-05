package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntryMapper
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiveConversionManagerTest {

    @Test
    fun fallbackPreservesFullInputWhenNoFullReadingMatch() {
        val manager = LiveConversionManager(enabled = true)
        val entry = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "これ",
            reading = "コレ",
            wordCost = 0,
            leftId = 0,
            rightId = 0,
            mid = 0,
        )
        val partialCandidate = Candidate(
            string = "これ",
            type = 1.toByte(),
            length = 2u,
            score = 0,
            data = listOf(entry),
        )

        val result = manager.updateWithNewResults(
            composingText = ComposingText.fromConvertTarget("これで"),
            candidates = listOf(partialCandidate),
            firstClauseResults = emptyList(),
            convertTargetCursorPosition = 3,
            convertTarget = "これで",
        )

        assertEquals("これで", result)
    }

    @Test
    fun selectsCandidateWhenFullReadingMatchesInput() {
        val manager = LiveConversionManager(enabled = true)
        val entry1 = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "変換",
            reading = "へんかん",
            wordCost = 0,
            leftId = 0,
            rightId = 0,
            mid = 0
        )
        val entry2 = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "固",
            reading = "こ",
            wordCost = 0,
            leftId = 0,
            rightId = 0,
            mid = 0
        )
        val candidate = Candidate(
            string = "変換固",
            type = 1.toByte(),
            length = 3u,
            score = 0,
            data = listOf(entry1, entry2)
        )

        val result = manager.updateWithNewResults(
            composingText = ComposingText.fromConvertTarget("へんかんこ"),
            candidates = listOf(candidate),
            firstClauseResults = emptyList(),
            convertTargetCursorPosition = 3,
            convertTarget = "へんかんこ"
        )

        assertEquals("変換こ", result)
    }

    @Test
    fun fallsBackToHiraganaWhenMainResultsLackFullReadingMatch() {
        val manager = LiveConversionManager(enabled = true)
        val entry = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "今日",
            reading = "キョウ",
            wordCost = 0,
            leftId = 1285,
            rightId = 1285,
            mid = 501,
        )
        val partialMain = Candidate(
            string = "今日",
            type = 1.toByte(),
            length = 2u,
            score = 0,
            data = listOf(entry),
        )
        val firstClause = Candidate(
            string = "今日は",
            type = 1.toByte(),
            length = 3u,
            score = 0,
            data = listOf(
                entry,
                AzooKeyDictionaryEntryMapper.systemDictionary("は", "ハ", 0, 1288, 1288, 501),
            ),
        )

        val result = manager.updateWithNewResults(
            composingText = ComposingText.fromConvertTarget("きょうは"),
            candidates = listOf(partialMain),
            firstClauseResults = listOf(firstClause),
            convertTargetCursorPosition = 3,
            convertTarget = "きょうは",
        )

        assertEquals("きょうは", result)
    }

    @Test
    fun selectsKatakanaCandidateForLiveConversionDisplayWhenReadingMatches() {
        val manager = LiveConversionManager(enabled = true)
        val katakana = Candidate(
            string = "アイウ",
            type = CandidateType.KATAKANA,
            length = 3u,
            score = 0,
            yomi = "アイウ",
            data = listOf(
                AzooKeyDictionaryEntryMapper.systemDictionary(
                    surface = "アイウ",
                    reading = "アイウ",
                    wordCost = 0,
                    leftId = 0,
                    rightId = 0,
                    mid = 0,
                ),
            ),
        )

        val result = manager.updateWithNewResults(
            composingText = ComposingText.fromConvertTarget("あいう"),
            candidates = listOf(katakana),
            firstClauseResults = emptyList(),
            convertTargetCursorPosition = 3,
            convertTarget = "あいう",
        )

        assertEquals("アイウ", result)
    }

    @Test
    fun rejectsHangulCandidateForLiveConversionDisplay() {
        val manager = LiveConversionManager(enabled = true)
        val hangul = Candidate(
            string = "한국",
            type = 1.toByte(),
            length = 2u,
            score = 0,
            data = listOf(
                AzooKeyDictionaryEntryMapper.systemDictionary(
                    surface = "한국",
                    reading = "カンコク",
                    wordCost = 0,
                    leftId = 0,
                    rightId = 0,
                    mid = 0,
                ),
            ),
        )

        val result = manager.updateWithNewResults(
            composingText = ComposingText.fromConvertTarget("かんこく"),
            candidates = listOf(hangul),
            firstClauseResults = emptyList(),
            convertTargetCursorPosition = 4,
            convertTarget = "かんこく",
        )

        assertEquals("かんこく", result)
    }

    @Test
    fun testFirstClauseAutoCompletion() {
        val manager = LiveConversionManager(enabled = true)
        
        val entry1 = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "今日",
            reading = "きょう",
            wordCost = 0,
            leftId = 1285,
            rightId = 1285,
            mid = 501
        )
        val entry2 = AzooKeyDictionaryEntryMapper.systemDictionary(
            surface = "は",
            reading = "は",
            wordCost = 0,
            leftId = 1288,
            rightId = 1288,
            mid = 501
        )

        val candidate1 = Candidate(
            string = "今日",
            type = 1.toByte(),
            length = 2u,
            score = 0,
            data = listOf(entry1)
        )

        val candidate2 = Candidate(
            string = "今日は",
            type = 1.toByte(),
            length = 3u,
            score = 0,
            data = listOf(entry1, entry2)
        )

        val candidate3 = Candidate(
            string = "今日はあ",
            type = 1.toByte(),
            length = 4u,
            score = 0,
            data = listOf(
                entry1,
                entry2,
                AzooKeyDictionaryEntryMapper.systemDictionary("あ", "あ", 0, 0, 0, 501)
            )
        )

        // Type character 1 (length diff > 0)
        manager.setLastUsedCandidate(candidate1)
        assertNull(manager.candidateForCompleteFirstClause(threshold = 3))

        // Type character 2 (length diff > 0)
        manager.setLastUsedCandidate(candidate2)
        assertNull(manager.candidateForCompleteFirstClause(threshold = 3))

        // Type character 3 (length diff > 0)
        manager.setLastUsedCandidate(candidate3)
        
        val autoCommitted = manager.candidateForCompleteFirstClause(threshold = 3)
        assertNotNull(autoCommitted)
        assertEquals("今日", autoCommitted!!.string)
    }
}
