package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyCid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionarySourceKind
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyMid
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Swift [PredictiveInputCacheTests] 相当。
 */
class AzooKeyPredictiveInputCacheTest {
    private fun makeCandidate(text: String, composingCount: ComposingCount = ComposingCount.SurfaceCount(text.length)): Candidate {
        val reading = text.hiraganaToKatakana()
        return Candidate(
            string = text,
            type = CandidateType.NBEST,
            length = text.length.toUByte(),
            score = 0,
            value = 0f,
            yomi = reading,
            composingCount = composingCount,
            lastMid = AzooKeyMid.GENERAL,
            data = listOf(
                AzooKeyDictionaryEntry(
                    surface = text,
                    reading = reading,
                    leftId = AzooKeyCid.PROPER_NOUN,
                    rightId = AzooKeyCid.PROPER_NOUN,
                    mid = AzooKeyMid.GENERAL,
                    wordCost = 0,
                    value = 0f,
                    sourceKind = AzooKeyDictionarySourceKind.System,
                ),
            ),
        )
    }

    @Test
    fun remainingPredictionReturnsUnconsumedSuffix() {
        val entry = PredictiveInputCacheEntry(
            context = PredictiveInputCacheContext(
                leftSideContext = "左文脈",
                inputStyle = InputStyle.Direct,
                zenzaiMode = AzooKeyStyleZenzaiMode.Off,
            ),
            originalConvertTarget = "あ",
            suffixCount = 0,
            predictedText = "いうえお",
        )
        assertEquals("うえお", entry.remainingPrediction("あい", 10))
        assertEquals("え", entry.remainingPrediction("あいう", 1))
    }

    @Test
    fun remainingPredictionHandlesRoman2KanaInsertion() {
        val entry = PredictiveInputCacheEntry(
            context = PredictiveInputCacheContext(
                leftSideContext = "",
                inputStyle = InputStyle.Roman2Kana,
                zenzaiMode = AzooKeyStyleZenzaiMode.Off,
            ),
            originalConvertTarget = "k",
            suffixCount = 1,
            predictedText = "カナ",
        )
        assertEquals("ナ", entry.remainingPrediction("か", 10))
        assertNull(entry.remainingPrediction("かな", 10))
    }

    @Test
    fun remainingPredictionReturnsNilWhenCurrentInputDiverges() {
        val entry = PredictiveInputCacheEntry(
            context = PredictiveInputCacheContext(
                leftSideContext = "",
                inputStyle = InputStyle.Direct,
                zenzaiMode = AzooKeyStyleZenzaiMode.Off,
            ),
            originalConvertTarget = "あ",
            suffixCount = 0,
            predictedText = "いう",
        )
        assertNull(entry.remainingPrediction("あか", 10))
        assertNull(entry.remainingPrediction("い", 10))
    }

    @Test
    fun stablePredictionCandidateCacheKeepsPrefixCompatibleCandidates() {
        val entry = StablePredictionCandidateCacheEntry(
            originalConvertTarget = "あいうえおかきくk",
            suffixCount = 1,
            candidates = listOf(
                makeCandidate("あいうえおかきくけこ"),
                makeCandidate("あいうえおかきくけど"),
                makeCandidate("別候補"),
            ),
        )
        assertEquals(
            listOf("あいうえおかきくけこ", "あいうえおかきくけど"),
            entry.compatibleCandidates(
                currentConvertTarget = "あいうえおかきくけ",
                baseConvertTarget = "あいうえおかきくけ",
                possibleNexts = emptyList(),
            ).map { it.string },
        )
    }

    @Test
    fun stablePredictionCandidateCacheUsesPossibleNextsForRomanSuffix() {
        val entry = StablePredictionCandidateCacheEntry(
            originalConvertTarget = "あいうえおかきくけ",
            suffixCount = 0,
            candidates = listOf(
                makeCandidate("あいうえおかきくけこ"),
                makeCandidate("あいうえおかきくけど"),
            ),
        )
        assertEquals(
            listOf("あいうえおかきくけこ"),
            entry.compatibleCandidates(
                currentConvertTarget = "あいうえおかきくけk",
                baseConvertTarget = "あいうえおかきくけ",
                possibleNexts = listOf("こ", "か"),
            ).map { it.string },
        )
    }

    @Test
    fun stablePredictionCandidateCacheUpdatesComposingCountForGrownDirectInput() {
        val entry = StablePredictionCandidateCacheEntry(
            originalConvertTarget = "おはよ",
            suffixCount = 0,
            candidates = listOf(
                makeCandidate(
                    text = "おはようございます",
                    composingCount = ComposingCount.SurfaceCount(3),
                ),
            ),
        )
        val candidates = entry.compatibleCandidates(
            currentConvertTarget = "おはよう",
            baseConvertTarget = "おはよう",
            possibleNexts = emptyList(),
        )
        assertEquals(listOf("おはようございます"), candidates.map { it.string })
        assertEquals(ComposingCount.SurfaceCount(4), candidates.first().composingCount)
    }
}
