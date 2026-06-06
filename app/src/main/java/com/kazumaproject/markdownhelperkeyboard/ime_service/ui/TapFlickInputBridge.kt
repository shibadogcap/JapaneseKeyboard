package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * [handleTapAndFlick] / [handleTapAndFlickFloating] の共有分岐（Phase 4b）。
 * Enter / Space / Delete / 文字キーなど surface 差分は [TapFlickSurfaceActions] へ委譲する。
 */
class TapFlickInputBridge {

    data class TapFlickDispatchRequest(
        val key: Key,
        val char: Char?,
        val insertString: String,
        val isFlick: Boolean,
        val gestureType: GestureType,
        val suggestions: List<Candidate>,
    )

    interface TapFlickSessionHooks {
        val vibrationTimingStr: String?
        fun vibrate()
        val selectModeActive: Boolean
        val deletedBufferNotEmpty: Boolean
        fun clearDeletedBuffer()
        fun clearDeletedBufferWithoutResetLayout()
        fun refreshEditHistoryUi()
        val leftCursorLongPressed: Boolean
        val rightCursorLongPressed: Boolean
        val deleteKeyLongPressed: Boolean
        val isHenkanActive: Boolean
        val cursorMoveModeActive: Boolean
        fun exitCursorMoveMode()
        val isSpaceKeyLongPressed: Boolean
        fun setSpaceKeyLongPressed(value: Boolean)
        val hankakuPreference: Boolean?
        val isDeleteLeftFlickPreference: Boolean?
        val isDeleteUpFlickPreference: Boolean?
        val isDeleteDownFlickPreference: Boolean?
        fun onLeftKeyLongPressReleased()
        fun onRightKeyLongPressReleased()
        fun stopDeleteLongPress()
        fun toggleSymbolKeyboard()
        fun finishComposingAndClearTail()
        fun performCopy()
        fun performCut()
        fun performSelectAll()
        fun performShareSelectedText()
    }

    interface TapFlickSurfaceActions {
        val surface: ImeKeyboardSurface
        fun handleNonEmptyEnter(insertString: String, suggestions: List<Candidate>)
        fun handleEmptyEnter()
        fun handleDakutenSmall(
            sb: java.lang.StringBuilder,
            isFlick: Boolean,
            char: Char?,
            insertString: String,
            gestureType: GestureType,
        )
        fun moveFocusedBunsetsu(delta: Int): Boolean
        fun handleDeleteKeyInHenkan(suggestions: List<Candidate>, insertString: String)
        fun handleLeftCursor(gestureType: GestureType, insertString: String)
        fun handleJapaneseModeSpaceKey(suggestions: List<Candidate>, insertString: String)
        fun actionInRightKeyPressed(gestureType: GestureType, insertString: String)
        fun handleDeleteKeyTap(insertString: String, suggestions: List<Candidate>)
        fun deleteWordOrSymbolsBeforeCursor(insertString: String)
        fun deleteWordOrSymbolsAfterCursor(insertString: String)
        fun undoLastHistoryEntry()
        fun setTenkeyIconsInHenkan(insertString: String)
        fun setNextReturnInputCharacter(insertString: String)
        fun cycleFocusedBunsetsuCandidate(delta: Int): Boolean
        fun handleSpaceKeyClick(isHankaku: Boolean, insertString: String, suggestions: List<Candidate>)
        fun handleFlick(char: Char?, insertString: String, sb: java.lang.StringBuilder)
        fun handleTap(char: Char?, insertString: String, sb: java.lang.StringBuilder)
    }

    fun dispatch(
        request: TapFlickDispatchRequest,
        sb: java.lang.StringBuilder,
        session: TapFlickSessionHooks,
        surface: TapFlickSurfaceActions,
    ) {
        applyVibration(session)
        applyDeletedBufferPolicy(request.key, session)
        when (request.key) {
            Key.NotSelected -> Unit
            Key.SideKeyEnter -> dispatchEnter(request, surface)
            Key.KeyDakutenSmall -> surface.handleDakutenSmall(
                sb = sb,
                isFlick = request.isFlick,
                char = request.char,
                insertString = request.insertString,
                gestureType = request.gestureType,
            )
            Key.SideKeyCursorLeft -> dispatchCursorLeft(request, session, surface)
            Key.SideKeyCursorRight -> dispatchCursorRight(request, session, surface)
            Key.SideKeyDelete -> dispatchDelete(request, session, surface)
            Key.SideKeyInputMode -> surface.setTenkeyIconsInHenkan(request.insertString)
            Key.SideKeyPreviousChar -> dispatchPreviousChar(request, surface)
            Key.SideKeySpace -> dispatchSpace(request, session, surface)
            Key.SideKeySymbol -> {
                session.vibrate()
                session.toggleSymbolKeyboard()
                session.finishComposingAndClearTail()
            }
            else -> dispatchCharacterOrSelection(request, sb, session, surface)
        }
    }

    private fun applyVibration(session: TapFlickSessionHooks) {
        when (session.vibrationTimingStr) {
            "both", "release" -> session.vibrate()
            else -> Unit
        }
    }

    private fun applyDeletedBufferPolicy(key: Key, session: TapFlickSessionHooks) {
        if (session.deletedBufferNotEmpty && !session.selectModeActive && key != Key.SideKeyDelete) {
            session.clearDeletedBuffer()
            session.refreshEditHistoryUi()
        } else if (session.deletedBufferNotEmpty && session.selectModeActive && key == Key.SideKeySpace) {
            session.clearDeletedBufferWithoutResetLayout()
            session.refreshEditHistoryUi()
        }
    }

    private fun dispatchEnter(request: TapFlickDispatchRequest, surface: TapFlickSurfaceActions) {
        if (request.insertString.isNotEmpty()) {
            surface.handleNonEmptyEnter(request.insertString, request.suggestions)
        } else {
            surface.handleEmptyEnter()
        }
    }

    private fun dispatchCursorLeft(
        request: TapFlickDispatchRequest,
        session: TapFlickSessionHooks,
        surface: TapFlickSurfaceActions,
    ) {
        if (!session.leftCursorLongPressed) {
            when {
                surface.moveFocusedBunsetsu(delta = -1) -> Unit
                session.isHenkanActive -> surface.handleDeleteKeyInHenkan(
                    request.suggestions,
                    request.insertString,
                )
                else -> surface.handleLeftCursor(request.gestureType, request.insertString)
            }
        }
        session.onLeftKeyLongPressReleased()
    }

    private fun dispatchCursorRight(
        request: TapFlickDispatchRequest,
        session: TapFlickSessionHooks,
        surface: TapFlickSurfaceActions,
    ) {
        if (!session.rightCursorLongPressed) {
            when {
                surface.moveFocusedBunsetsu(delta = 1) -> Unit
                session.isHenkanActive -> surface.handleJapaneseModeSpaceKey(
                    request.suggestions,
                    request.insertString,
                )
                else -> surface.actionInRightKeyPressed(request.gestureType, request.insertString)
            }
        }
        session.onRightKeyLongPressReleased()
    }

    private fun dispatchDelete(
        request: TapFlickDispatchRequest,
        session: TapFlickSessionHooks,
        surface: TapFlickSurfaceActions,
    ) {
        if (!request.isFlick) {
            if (!session.deleteKeyLongPressed) {
                surface.handleDeleteKeyTap(request.insertString, request.suggestions)
            }
        } else {
            when (request.gestureType) {
                GestureType.FlickLeft -> {
                    if (session.isDeleteLeftFlickPreference == true) {
                        surface.deleteWordOrSymbolsBeforeCursor(request.insertString)
                    }
                }
                GestureType.FlickTop -> {
                    if (session.isDeleteUpFlickPreference == true) {
                        surface.deleteWordOrSymbolsAfterCursor(request.insertString)
                    }
                }
                GestureType.FlickBottom -> {
                    if (session.isDeleteDownFlickPreference == true) {
                        surface.undoLastHistoryEntry()
                    }
                }
                else -> Unit
            }
        }
        session.stopDeleteLongPress()
    }

    private fun dispatchPreviousChar(request: TapFlickDispatchRequest, surface: TapFlickSurfaceActions) {
        when (surface.surface.currentTenkeyInputMode()) {
            is InputMode.ModeNumber -> Unit
            else -> if (!request.isFlick) surface.setNextReturnInputCharacter(request.insertString)
        }
    }

    private fun dispatchSpace(
        request: TapFlickDispatchRequest,
        session: TapFlickSessionHooks,
        surface: TapFlickSurfaceActions,
    ) {
        if (session.cursorMoveModeActive) {
            session.exitCursorMoveMode()
        } else if (!session.isSpaceKeyLongPressed) {
            when {
                request.gestureType == GestureType.FlickLeft &&
                    surface.cycleFocusedBunsetsuCandidate(delta = -1) -> Unit
                request.gestureType == GestureType.FlickLeft -> {
                    val isHankaku = session.hankakuPreference == true
                    surface.handleSpaceKeyClick(!isHankaku, request.insertString, request.suggestions)
                }
                else -> {
                    val isHankaku = session.hankakuPreference == true
                    surface.handleSpaceKeyClick(isHankaku, request.insertString, request.suggestions)
                }
            }
        }
        session.setSpaceKeyLongPressed(false)
    }

    private fun dispatchCharacterOrSelection(
        request: TapFlickDispatchRequest,
        sb: java.lang.StringBuilder,
        session: TapFlickSessionHooks,
        surface: TapFlickSurfaceActions,
    ) {
        if (session.selectModeActive) {
            when (request.key) {
                Key.KeyA -> session.performCopy()
                Key.KeySA -> session.performCut()
                Key.KeyMA -> session.performSelectAll()
                Key.KeyRA -> session.performShareSelectedText()
                else -> Unit
            }
        } else if (request.isFlick) {
            surface.handleFlick(request.char, request.insertString, sb)
        } else {
            surface.handleTap(request.char, request.insertString, sb)
        }
    }
}