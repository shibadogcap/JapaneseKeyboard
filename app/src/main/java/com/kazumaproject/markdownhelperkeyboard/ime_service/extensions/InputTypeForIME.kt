package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import com.kazumaproject.core.domain.key.EnterKeyVisual
import com.kazumaproject.core.domain.key.englishLabel
import com.kazumaproject.core.domain.key.japaneseLabel
import com.kazumaproject.core.domain.key.toSumireEnterKeyIndex
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME

fun InputTypeForIME.getEnterKeyVisual(): EnterKeyVisual {
    return when (this) {
        InputTypeForIME.TextMultiLine,
        InputTypeForIME.TextImeMultiLine,
        InputTypeForIME.TextShortMessage,
        InputTypeForIME.TextLongMessage,
            -> EnterKeyVisual.RETURN

        InputTypeForIME.TextEmailAddress,
        InputTypeForIME.TextEmailSubject,
        InputTypeForIME.TextNextLine,
            -> EnterKeyVisual.TAB

        InputTypeForIME.TextDone -> EnterKeyVisual.CHECK

        InputTypeForIME.TextWebSearchView,
        InputTypeForIME.TextWebSearchViewFireFox,
        InputTypeForIME.TextSearchView,
            -> EnterKeyVisual.SEARCH

        InputTypeForIME.TextSend -> EnterKeyVisual.ARROW

        else -> EnterKeyVisual.ARROW
    }
}

fun InputTypeForIME.getQWERTYReturnTextInJp(): String {
    return getEnterKeyVisual().japaneseLabel()
}

fun InputTypeForIME.getQWERTYReturnTextInEn(): String {
    return getEnterKeyVisual().englishLabel()
}

fun InputTypeForIME.getEnterKeyIndexSumire(): Int {
    return getEnterKeyVisual().toSumireEnterKeyIndex()
}
