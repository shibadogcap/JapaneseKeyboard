package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * Main / Floating で差分のある tap/flick 委譲のみ [Bindings] に残し、それ以外は [SharedHost] へ集約する。
 */
object TapFlickSurfaceActionsFactory {

    interface SharedHost {
        fun handleDeleteKeyInHenkan(suggestions: List<Candidate>, insertString: String)
        fun handleLeftCursor(gestureType: GestureType, insertString: String)
        fun actionInRightKeyPressed(gestureType: GestureType, insertString: String)
        fun handleDeleteKeyTap(insertString: String, suggestions: List<Candidate>)
        fun deleteWordOrSymbolsBeforeCursor(insertString: String)
        fun deleteWordOrSymbolsAfterCursor(insertString: String)
        fun undoLastHistoryEntry()
        fun setNextReturnInputCharacter(insertString: String)
    }

    data class Bindings(
        val surface: ImeKeyboardSurface,
        val onNonEmptyEnter: (insertString: String, suggestions: List<Candidate>) -> Unit,
        val onEmptyEnter: () -> Unit,
        val onDakutenSmall: (
            sb: StringBuilder,
            isFlick: Boolean,
            char: Char?,
            insertString: String,
            gestureType: GestureType,
        ) -> Unit,
        val moveFocusedBunsetsu: (delta: Int) -> Boolean,
        val onJapaneseModeSpaceKey: (suggestions: List<Candidate>, insertString: String) -> Unit,
        val setTenkeyIconsInHenkan: (insertString: String) -> Unit,
        val cycleFocusedBunsetsuCandidate: (delta: Int) -> Boolean,
        val onSpaceKeyClick: (isHankaku: Boolean, insertString: String, suggestions: List<Candidate>) -> Unit,
        val onFlick: (char: Char?, insertString: String, sb: StringBuilder) -> Unit,
        val onTap: (char: Char?, insertString: String, sb: StringBuilder) -> Unit,
    )

    fun create(
        shared: SharedHost,
        bindings: Bindings,
    ): TapFlickInputBridge.TapFlickSurfaceActions {
        return object : TapFlickInputBridge.TapFlickSurfaceActions {
            override val surface: ImeKeyboardSurface = bindings.surface
            override fun handleNonEmptyEnter(insertString: String, suggestions: List<Candidate>) =
                bindings.onNonEmptyEnter(insertString, suggestions)
            override fun handleEmptyEnter() = bindings.onEmptyEnter()
            override fun handleDakutenSmall(
                sb: StringBuilder,
                isFlick: Boolean,
                char: Char?,
                insertString: String,
                gestureType: GestureType,
            ) = bindings.onDakutenSmall(sb, isFlick, char, insertString, gestureType)
            override fun moveFocusedBunsetsu(delta: Int): Boolean = bindings.moveFocusedBunsetsu(delta)
            override fun handleDeleteKeyInHenkan(
                suggestions: List<Candidate>,
                insertString: String,
            ) = shared.handleDeleteKeyInHenkan(suggestions, insertString)
            override fun handleLeftCursor(gestureType: GestureType, insertString: String) =
                shared.handleLeftCursor(gestureType, insertString)
            override fun handleJapaneseModeSpaceKey(
                suggestions: List<Candidate>,
                insertString: String,
            ) = bindings.onJapaneseModeSpaceKey(suggestions, insertString)
            override fun actionInRightKeyPressed(gestureType: GestureType, insertString: String) =
                shared.actionInRightKeyPressed(gestureType, insertString)
            override fun handleDeleteKeyTap(insertString: String, suggestions: List<Candidate>) =
                shared.handleDeleteKeyTap(insertString, suggestions)
            override fun deleteWordOrSymbolsBeforeCursor(insertString: String) =
                shared.deleteWordOrSymbolsBeforeCursor(insertString)
            override fun deleteWordOrSymbolsAfterCursor(insertString: String) =
                shared.deleteWordOrSymbolsAfterCursor(insertString)
            override fun undoLastHistoryEntry() = shared.undoLastHistoryEntry()
            override fun setTenkeyIconsInHenkan(insertString: String) =
                bindings.setTenkeyIconsInHenkan(insertString)
            override fun setNextReturnInputCharacter(insertString: String) =
                shared.setNextReturnInputCharacter(insertString)
            override fun cycleFocusedBunsetsuCandidate(delta: Int): Boolean =
                bindings.cycleFocusedBunsetsuCandidate(delta)
            override fun handleSpaceKeyClick(
                isHankaku: Boolean,
                insertString: String,
                suggestions: List<Candidate>,
            ) = bindings.onSpaceKeyClick(isHankaku, insertString, suggestions)
            override fun handleFlick(char: Char?, insertString: String, sb: StringBuilder) =
                bindings.onFlick(char, insertString, sb)
            override fun handleTap(char: Char?, insertString: String, sb: StringBuilder) =
                bindings.onTap(char, insertString, sb)
        }
    }
}