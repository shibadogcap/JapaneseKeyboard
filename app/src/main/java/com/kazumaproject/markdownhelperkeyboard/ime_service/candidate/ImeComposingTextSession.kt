package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingCount
import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.appendRoman2KanaCharAtEnd
import com.kazumaproject.markdownhelperkeyboard.converter.api.insertDirectAtCursor
import com.kazumaproject.markdownhelperkeyboard.converter.api.insertRoman2KanaAtCursor
import com.kazumaproject.markdownhelperkeyboard.converter.api.prefixComplete
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiComposingSnapshot

/**
 * composing 中の [ComposingText] をキー入力単位で保持する。
 * 候補リクエスト時にスナップショットから毎回組み立て直すのではなく、セッション上の状態を返す。
 */
class ImeComposingTextSession {
    private var roman2Kana: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity
    private var composingText: ComposingText = ComposingText.fromConvertTarget("")
    private var lastRomajiSnapshot: RomajiComposingSnapshot? = null
    private var lastQwertyRawInput: String? = null
    private var qwertyModeActive: Boolean = false

    fun configure(transducer: AzooKeyRoman2KanaTransducer) {
        roman2Kana = transducer
    }

    fun reset() {
        composingText = ComposingText.fromConvertTarget("")
        lastRomajiSnapshot = null
        lastQwertyRawInput = null
        qwertyModeActive = false
    }

    fun current(): ComposingText = composingText

    fun isActive(): Boolean = qwertyModeActive

    /** AzooKey [ComposingText.prefixComplete] 相当。文節部分確定後に composing 状態を更新する。 */
    fun prefixComplete(composingCount: ComposingCount, roman2Kana: AzooKeyRoman2KanaTransducer) {
        composingText = composingText.prefixComplete(composingCount, roman2Kana)
    }

    /** フリック等の直接かな入力 */
    fun applyDirectInput(displayInput: String) {
        qwertyModeActive = false
        lastRomajiSnapshot = null
        lastQwertyRawInput = null
        composingText = ComposingText.fromConvertTarget(displayInput)
    }

    /** 物理キーボード経路: [RomajiKanaConverter] のスナップショットをセッションへ反映 */
    fun applyPhysicalKeyboardSnapshot(
        snapshot: RomajiComposingSnapshot,
        displayInput: String,
    ) {
        qwertyModeActive = true
        if (tryAppendPhysicalSnapshotDelta(snapshot)) {
            lastRomajiSnapshot = snapshot
            return
        }
        lastRomajiSnapshot = snapshot
        rebuildFromRomajiSnapshot(snapshot, displayInput)
    }

    /**
     * 画面上 QWERTY（`convertQWERTYZenkaku` 系）の生入力。
     * 1 文字追加のときは [ComposingTextEditor] で差分 append し、それ以外はセグメント再構築する。
     */
    fun rebuildFromQwertyRawInput(
        rawInput: String,
        zenkakuRomaji: Boolean,
        displayInput: String = rawInput,
    ) {
        qwertyModeActive = true
        lastRomajiSnapshot = null
        if (rawInput.isEmpty()) {
            composingText = ComposingText.fromConvertTarget("")
            lastQwertyRawInput = rawInput
            return
        }
        if (tryAppendQwertyRawDelta(rawInput, zenkakuRomaji, displayInput)) {
            lastQwertyRawInput = rawInput
            return
        }
        composingText = buildComposingFromQwertyRaw(rawInput, zenkakuRomaji)
        lastQwertyRawInput = rawInput
    }

    fun rebuildFromRomajiSnapshot(
        snapshot: RomajiComposingSnapshot,
        displayInput: String,
    ) {
        qwertyModeActive = true
        lastRomajiSnapshot = snapshot
        lastQwertyRawInput = null
        composingText = ImeCandidateRequestFactory.composingText(
            displayInput = displayInput,
            qwerty = snapshot,
            roman2Kana = roman2Kana,
        )
    }

    fun resolveForCandidateRequest(
        displayInput: String,
        useQwertySession: Boolean,
        fallbackSnapshot: RomajiComposingSnapshot?,
    ): ComposingText {
        if (!useQwertySession) {
            return ComposingText.fromConvertTarget(displayInput)
        }
        if (qwertyModeActive && composingText.convertTarget.isNotEmpty()) {
            if (composingText.convertTarget == displayInput) {
                return composingText
            }
        }
        fallbackSnapshot?.let { snapshot ->
            if (snapshot.displayText == displayInput) {
                rebuildFromRomajiSnapshot(snapshot, displayInput)
                return composingText
            }
        }
        return ComposingText.fromConvertTarget(displayInput)
    }

    private fun tryAppendPhysicalSnapshotDelta(snapshot: RomajiComposingSnapshot): Boolean {
        val previous = lastRomajiSnapshot ?: return false
        if (previous.committedSurface != snapshot.committedSurface) return false
        val pending = snapshot.pendingRomaji
        val lastPending = previous.pendingRomaji
        if (!pending.startsWith(lastPending) || pending.length != lastPending.length + 1) {
            return false
        }
        val appended = pending.last()
        composingText = appendQwertyChar(
            composingText = composingText,
            char = appended,
            zenkakuRomaji = appended in ROMAN2KANA_ZENKAKU_ALPHABET || appended == 'ｎ',
        )
        return composingText.convertTarget == snapshot.displayText
    }

    private fun tryAppendQwertyRawDelta(
        rawInput: String,
        zenkakuRomaji: Boolean,
        displayInput: String,
    ): Boolean {
        val previous = lastQwertyRawInput ?: return false
        if (!rawInput.startsWith(previous) || rawInput.length != previous.length + 1) {
            return false
        }
        val appended = rawInput.last()
        composingText = appendQwertyChar(
            composingText = composingText,
            char = appended,
            zenkakuRomaji = zenkakuRomaji,
        )
        return composingText.convertTarget == displayInput
    }

    private fun appendQwertyChar(
        composingText: ComposingText,
        char: Char,
        zenkakuRomaji: Boolean,
    ): ComposingText {
        val romajiAlphabet = if (zenkakuRomaji) {
            ROMAN2KANA_ZENKAKU_ALPHABET
        } else {
            ROMAN2KANA_HALFWIDTH_ALPHABET
        }
        return if (isRomajiChar(char, zenkakuRomaji, romajiAlphabet)) {
            composingText.appendRoman2KanaCharAtEnd(char, roman2Kana)
        } else {
            composingText.insertDirectAtCursor(char.toString())
        }
    }

    private fun buildComposingFromQwertyRaw(rawInput: String, zenkakuRomaji: Boolean): ComposingText {
        val romajiAlphabet = if (zenkakuRomaji) {
            ROMAN2KANA_ZENKAKU_ALPHABET
        } else {
            ROMAN2KANA_HALFWIDTH_ALPHABET
        }
        var text = ComposingText.fromConvertTarget("")
        var index = 0
        while (index < rawInput.length) {
            if (isRomajiChar(rawInput[index], zenkakuRomaji, romajiAlphabet)) {
                val start = index
                index++
                while (index < rawInput.length &&
                    isRomajiChar(rawInput[index], zenkakuRomaji, romajiAlphabet)
                ) {
                    index++
                }
                text = text.insertRoman2KanaAtCursor(rawInput.substring(start, index), roman2Kana)
            } else {
                val start = index
                index++
                while (index < rawInput.length &&
                    !isRomajiChar(rawInput[index], zenkakuRomaji, romajiAlphabet)
                ) {
                    index++
                }
                text = text.insertDirectAtCursor(rawInput.substring(start, index))
            }
        }
        return text
    }

    private fun isRomajiChar(
        char: Char,
        zenkakuRomaji: Boolean,
        romajiAlphabet: Set<Char>,
    ): Boolean {
        if (char in romajiAlphabet) return true
        return zenkakuRomaji && char == 'ｎ' || !zenkakuRomaji && char == 'n'
    }

    companion object {
        private val ROMAN2KANA_HALFWIDTH_ALPHABET =
            ('a'..'z').toSet() + setOf('-', '.', ',', '[', ']')
        private val ROMAN2KANA_ZENKAKU_ALPHABET =
            ('ａ'..'ｚ').toSet() + setOf('ｰ', '。', '、', '［', '］', '「', '」')
    }
}