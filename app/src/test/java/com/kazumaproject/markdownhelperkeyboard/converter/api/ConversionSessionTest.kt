package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionSessionTest {
    @Test
    fun recordConversionTracksComposingWithoutClearingCompletedCandidate() {
        val session = ConversionSession()
        session.setCompletedData(committed("前回"))
        val composing = ComposingText.fromConvertTarget("しかい")

        session.recordConversion(composing, bunsetsuResult = null)

        assertEquals(composing, session.previousComposingText)
        assertEquals("しかい", session.lastConvertTarget)
        assertEquals(committed("前回"), session.completedCandidate)
    }

    @Test
    fun consumeCompletedDataClearsPartialCommitSeed() {
        val session = ConversionSession()
        session.setCompletedData(committed("今日"))
        session.consumeCompletedData()
        assertNull(session.completedCandidate)
    }

    @Test
    fun isIncrementalExtensionDetectsPrefixGrowth() {
        val session = ConversionSession()
        session.recordConversion(ComposingText.fromConvertTarget("しか"))

        assertTrue(session.isIncrementalExtension(ComposingText.fromConvertTarget("しかい")))
        assertFalse(session.isIncrementalExtension(ComposingText.fromConvertTarget("かい")))
        assertFalse(session.isIncrementalExtension(ComposingText.fromConvertTarget("しか")))
    }

    @Test
    fun recordCommitPreservesYomiAndStopsComposition() {
        val session = ConversionSession()
        session.recordConversion(ComposingText.fromConvertTarget("しかい"))

        val committed = session.recordCommit(
            surface = "視界",
            tapped = Candidate(
                string = "視界",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 10,
                value = -10f,
                yomi = "しかい",
            ),
        )

        assertEquals("視界", committed.string)
        assertEquals("しかい", committed.yomi)
        assertEquals(committed, session.lastCommittedCandidate)
        assertNull(session.lastConvertTarget)
        assertNull(session.previousComposingText)
    }

    @Test
    fun zenzRerankCacheEvictsOldestAfterLimit() {
        val session = ConversionSession()
        repeat(65) { index ->
            session.putZenzRerank("key$index", listOf(committed("候補$index")))
        }

        assertNull(session.getZenzRerank("key0"))
        assertEquals(listOf("候補64"), session.getZenzRerank("key64")?.map { it.string })
    }

    @Test
    fun resetClearsAllSessionState() {
        val session = ConversionSession()
        session.recordConversion(ComposingText.fromConvertTarget("あ"))
        session.recordCommit(surface = "亜", fallbackReading = "あ")
        session.putZenzRerank("k", listOf(committed("x")))
        session.latticeIncrementalState.normalizedInput = "ア"
        session.latticeIncrementalState.latticeNodes = listOf(
            com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeNode(
                entry = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntryMapper.memory(
                    surface = "亜",
                    reading = "あ",
                    leftId = 1285,
                    rightId = 1285,
                    legacyScore = 0,
                    readingLength = 1,
                ),
                startIndex = 0,
                endIndex = 1,
            ),
        )

        session.reset()

        assertNull(session.lastCommittedCandidate)
        assertNull(session.getZenzRerank("k"))
        assertNull(session.latticeIncrementalState.normalizedInput)
        assertTrue(session.latticeIncrementalState.latticeNodes.isEmpty())
    }

    @Test
    fun leftSideContextTracksCommitsAndClearsOnReset() {
        val session = ConversionSession()
        assertEquals("", session.leftSideContext)

        // First commit
        session.recordCommit(
            surface = "こんにちは",
            tapped = Candidate(
                string = "こんにちは",
                type = CandidateType.NBEST,
                length = 5.toUByte(),
                score = 10,
                value = -10f,
                yomi = "こんにちは",
            ),
        )
        assertEquals("こんにちは", session.leftSideContext)

        // Second commit
        session.recordCommit(
            surface = "世界",
            tapped = Candidate(
                string = "世界",
                type = CandidateType.NBEST,
                length = 2.toUByte(),
                score = 10,
                value = -10f,
                yomi = "せかい",
            ),
        )
        assertEquals("こんにちは世界", session.leftSideContext)

        // Clear composition drops context
        session.stopComposition(keepCommitted = false)
        assertEquals("", session.leftSideContext)

        // Re-commit
        session.recordCommit(surface = "テスト", fallbackReading = "てすと")
        assertEquals("テスト", session.leftSideContext)

        // Reset completely drops context
        session.reset()
        assertEquals("", session.leftSideContext)
    }

    private fun committed(string: String): Candidate {
        return Candidate(
            string = string,
            type = CandidateType.NBEST,
            length = string.length.toUByte(),
            score = 0,
        )
    }
}