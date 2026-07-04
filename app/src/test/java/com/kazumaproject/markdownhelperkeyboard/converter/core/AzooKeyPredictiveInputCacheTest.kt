package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AzooKeyPredictiveInputCacheTest {
    @Test
    fun remainingPredictionReturnsSuffixAfterConsumedPrefix() {
        val entry = PredictiveInputCacheEntry(
            context = PredictiveInputCacheContext(
                leftSideContext = "",
                inputStyle = InputStyle.Roman2Kana,
                zenzaiMode = AzooKeyStyleZenzaiMode.Off,
            ),
            originalConvertTarget = "きょうは",
            suffixCount = 0,
            predictedText = "いいてんき",
        )
        assertEquals("い", entry.remainingPrediction("きょうは", 1))
        assertEquals("いて", entry.remainingPrediction("きょうはい", 2))
    }

    @Test
    fun invalidatesWhenBaseMismatch() {
        val entry = PredictiveInputCacheEntry(
            context = PredictiveInputCacheContext(
                leftSideContext = "",
                inputStyle = InputStyle.Direct,
                zenzaiMode = AzooKeyStyleZenzaiMode.Off,
            ),
            originalConvertTarget = "あした",
            suffixCount = 0,
            predictedText = "はれ",
        )
        assertNull(entry.remainingPrediction("きょう", 1))
    }
}
