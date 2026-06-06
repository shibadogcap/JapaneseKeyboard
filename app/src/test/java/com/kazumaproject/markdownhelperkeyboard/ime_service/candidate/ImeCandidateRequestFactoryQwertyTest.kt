package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiComposingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeCandidateRequestFactoryQwertyTest {
    private val transducer = AzooKeyRoman2KanaTransducer.fromMap(
        mapOf(
            "si" to ("し" to 2),
            "ka" to ("か" to 2),
            "i" to ("い" to 1),
            "shi" to ("し" to 3),
            "kai" to ("かい" to 3),
        ),
    )

    @Test
    fun pendingRomajiBuildsRoman2KanaSegment() {
        val snapshot = RomajiComposingSnapshot(
            committedSurface = "か",
            pendingRomaji = "kai",
        )
        val text = ImeCandidateRequestFactory.composingText(
            displayInput = "かkai",
            qwerty = snapshot,
            roman2Kana = transducer,
        )
        assertEquals("かかい", text.convertTarget)
    }

    @Test
    fun fallsBackWhenDisplayDoesNotMatchSnapshot() {
        val snapshot = RomajiComposingSnapshot(
            committedSurface = "か",
            pendingRomaji = "kai",
        )
        val text = ImeCandidateRequestFactory.composingText(
            displayInput = "かかい",
            qwerty = snapshot,
            roman2Kana = transducer,
        )
        assertEquals("かかい", text.convertTarget)
    }

    @Test
    fun noPendingUsesPlainConvertTarget() {
        val snapshot = RomajiComposingSnapshot(
            committedSurface = "しかい",
            pendingRomaji = "",
        )
        val text = ImeCandidateRequestFactory.composingText(
            displayInput = "しかい",
            qwerty = snapshot,
            roman2Kana = transducer,
        )
        assertEquals("しかい", text.convertTarget)
    }
}