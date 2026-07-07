package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyPostCommitEmojiCollectorTest {
    @Test
    fun collectUsesCandidateDataWordsWithMmFilter() {
        val textReplacer = AzooKeyTextReplacer.fromEmojiTextReplacerText(
            """
            🎂	ケーキ,たんじょうび	🥳
            🍣	すし	
            """.trimIndent()
        )
        val candidate = Candidate(
            string = "寿司とケーキ",
            type = CandidateType.NBEST,
            length = 5u,
            score = 0,
            data = listOf(
                AzooKeyDictionaryEntry(
                    surface = "すし",
                    reading = "スシ",
                    leftId = AzooKeyCid.GENERAL_NOUN,
                    rightId = AzooKeyCid.GENERAL_NOUN,
                    mid = AzooKeyMid.GENERAL,
                    wordCost = 0,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                ),
                AzooKeyDictionaryEntry(
                    surface = "ケーキ",
                    reading = "ケーキ",
                    leftId = AzooKeyCid.GENERAL_NOUN,
                    rightId = AzooKeyCid.GENERAL_NOUN,
                    mid = AzooKeyMid.GENERAL,
                    wordCost = 0,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                ),
            ),
        )

        val result = AzooKeyPostCommitEmojiCollector.collect(candidate, textReplacer, limit = 5)

        assertEquals(listOf("🍣", "🎂"), result.map { it.string })
    }
}
