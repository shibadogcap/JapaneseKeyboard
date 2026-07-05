package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiComposingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeComposingTextSessionTest {
    private val transducer = AzooKeyRoman2KanaTransducer.fromMap(
        mapOf(
            "si" to ("し" to 2),
            "ka" to ("か" to 2),
            "i" to ("い" to 1),
            "kai" to ("かい" to 3),
        ),
    )

    @Test
    fun physicalSnapshotUpdatesSessionEachKey() {
        val session = ImeComposingTextSession().apply { configure(transducer) }
        session.applyPhysicalKeyboardSnapshot(
            RomajiComposingSnapshot(committedSurface = "か", pendingRomaji = "kai"),
            displayInput = "かkai",
        )
        assertEquals("かかい", session.current().convertTarget)
        assertEquals("かかい", session.resolveForCandidateRequest("かかい", true, null).convertTarget)
    }

    @Test
    fun rebuildFromQwertyRawInputSplitsKanaAndRomaji() {
        val session = ImeComposingTextSession().apply { configure(transducer) }
        session.rebuildFromQwertyRawInput("かkai", zenkakuRomaji = false)
        assertEquals("かかい", session.current().convertTarget)
    }

    @Test
    fun incrementalQwertyAppendBuildsKaiSegment() {
        val session = ImeComposingTextSession().apply { configure(transducer) }
        session.rebuildFromQwertyRawInput("か", zenkakuRomaji = false)
        session.rebuildFromQwertyRawInput("かk", zenkakuRomaji = false)
        session.rebuildFromQwertyRawInput("かka", zenkakuRomaji = false)
        session.rebuildFromQwertyRawInput("かkai", zenkakuRomaji = false)
        assertEquals("かかい", session.current().convertTarget)
    }

    @Test
    fun incrementalPhysicalSnapshotAppendsOneRomajiKey() {
        val session = ImeComposingTextSession().apply { configure(transducer) }
        session.applyPhysicalKeyboardSnapshot(
            RomajiComposingSnapshot(committedSurface = "か", pendingRomaji = "k"),
            displayInput = "かk",
        )
        session.applyPhysicalKeyboardSnapshot(
            RomajiComposingSnapshot(committedSurface = "か", pendingRomaji = "ka"),
            displayInput = "かka",
        )
        session.applyPhysicalKeyboardSnapshot(
            RomajiComposingSnapshot(committedSurface = "か", pendingRomaji = "kai"),
            displayInput = "かkai",
        )
        assertEquals("かかい", session.current().convertTarget)
    }

    @Test
    fun qwertyDeltaFallsBackToFullRebuildWhenDisplayDrifts() {
        val transducer = AzooKeyRoman2KanaTransducer.fromMap(
            mapOf("ka" to ("か" to 2)),
        )
        val session = ImeComposingTextSession().apply { configure(transducer) }
        session.rebuildFromQwertyRawInput(
            rawInput = "k",
            zenkakuRomaji = false,
            displayInput = "k",
        )
        session.rebuildFromQwertyRawInput(
            rawInput = "ka",
            zenkakuRomaji = false,
            displayInput = "か",
        )
        assertEquals("か", session.current().convertTarget)
    }

    @Test
    fun resolveUsesSessionWhenDisplayMatches() {
        val session = ImeComposingTextSession().apply { configure(transducer) }
        session.rebuildFromQwertyRawInput("しかい", zenkakuRomaji = false)
        val resolved = session.resolveForCandidateRequest(
            displayInput = "しかい",
            useQwertySession = true,
            fallbackSnapshot = null,
        )
        assertEquals("しかい", resolved.convertTarget)
    }
}