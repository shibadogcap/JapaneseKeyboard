package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import com.kazumaproject.core.domain.key.EnterKeyVisual
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import org.junit.Assert.assertEquals
import org.junit.Test

class EnterKeyVisualMappingTest {

    @Test
    fun multilineInputTypeUsesReturnVisual() {
        listOf(
            InputTypeForIME.TextMultiLine,
            InputTypeForIME.TextImeMultiLine,
            InputTypeForIME.TextShortMessage,
            InputTypeForIME.TextLongMessage,
        ).forEach { inputType ->
            assertEquals(EnterKeyVisual.RETURN, inputType.getEnterKeyVisual())
            assertEquals(0, inputType.getEnterKeyIndexSumire())
        }
    }

    @Test
    fun nextFieldInputTypeUsesTabVisual() {
        listOf(
            InputTypeForIME.TextEmailAddress,
            InputTypeForIME.TextEmailSubject,
            InputTypeForIME.TextNextLine,
        ).forEach { inputType ->
            assertEquals(EnterKeyVisual.TAB, inputType.getEnterKeyVisual())
            assertEquals(4, inputType.getEnterKeyIndexSumire())
        }
    }

    @Test
    fun doneInputTypeUsesCheckVisual() {
        assertEquals(EnterKeyVisual.CHECK, InputTypeForIME.TextDone.getEnterKeyVisual())
        assertEquals(5, InputTypeForIME.TextDone.getEnterKeyIndexSumire())
    }

    @Test
    fun searchInputTypeUsesSearchVisual() {
        listOf(
            InputTypeForIME.TextWebSearchView,
            InputTypeForIME.TextWebSearchViewFireFox,
            InputTypeForIME.TextSearchView,
        ).forEach { inputType ->
            assertEquals(EnterKeyVisual.SEARCH, inputType.getEnterKeyVisual())
            assertEquals(3, inputType.getEnterKeyIndexSumire())
        }
    }

    @Test
    fun defaultSingleLineTextUsesArrowVisual() {
        listOf(
            InputTypeForIME.Text,
            InputTypeForIME.TextAutoComplete,
            InputTypeForIME.TextAutoCorrect,
            InputTypeForIME.TextCapCharacters,
            InputTypeForIME.TextCapSentences,
            InputTypeForIME.TextCapWords,
            InputTypeForIME.TextFilter,
            InputTypeForIME.TextNoSuggestion,
            InputTypeForIME.TextPersonName,
            InputTypeForIME.TextPhonetic,
            InputTypeForIME.TextWebEditText,
            InputTypeForIME.TextUri,
            InputTypeForIME.TextSend,
            InputTypeForIME.Number,
            InputTypeForIME.Phone,
        ).forEach { inputType ->
            assertEquals(EnterKeyVisual.ARROW, inputType.getEnterKeyVisual())
            assertEquals(1, inputType.getEnterKeyIndexSumire())
        }
    }

    @Test
    fun qwertyLabelsMatchVisual() {
        assertEquals("改行", InputTypeForIME.TextMultiLine.getQWERTYReturnTextInJp())
        assertEquals("return", InputTypeForIME.TextMultiLine.getQWERTYReturnTextInEn())
        assertEquals("検索", InputTypeForIME.TextSearchView.getQWERTYReturnTextInJp())
        assertEquals("search", InputTypeForIME.TextSearchView.getQWERTYReturnTextInEn())
        assertEquals("done", InputTypeForIME.TextDone.getQWERTYReturnTextInEn())
    }
}
