package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLiveConversionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputElement
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputPiece
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CandidateRequestBridgeTest {
    @Test
    fun bridgeMapsComposingTextAndRuntimeFlags() {
        val request = CandidateRequestBridge.toCandidateRequest(
            composingText = ComposingText.fromConvertTarget("しかい"),
            options = ConvertRequestOptions(
                nBest = 6,
                requireJapanesePrediction = AzooKeyStylePredictionMode.AutoMix,
            ),
            runtime = ConvertRuntimeContext(
                liveConversionMode = AzooKeyLiveConversionMode.Enabled,
                isConverting = true,
            ),
            mode = CandidateRequestMode.Normal,
        )

        assertEquals("しかい", request.input)
        assertEquals(6, request.nBest)
        assertFalse(request.shouldUseLiveConversion)
    }

    @Test
    fun bridgeUsesPrefixBeforeCursor() {
        val request = CandidateRequestBridge.toCandidateRequest(
            composingText = ComposingText(
                convertTarget = "しかい",
                convertTargetCursorPosition = 2,
                input = listOf(
                    InputElement(InputPiece.Character('し'), InputStyle.Direct),
                    InputElement(InputPiece.Character('か'), InputStyle.Direct),
                    InputElement(InputPiece.Character('い'), InputStyle.Direct),
                ),
            ),
            options = ConvertRequestOptions(),
            runtime = ConvertRuntimeContext(),
            mode = CandidateRequestMode.Normal,
        )
        assertEquals("しか", request.input)
    }
}