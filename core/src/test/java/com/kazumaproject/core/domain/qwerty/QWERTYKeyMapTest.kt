package com.kazumaproject.core.domain.qwerty
import com.kazumaproject.core.data.qwerty.QWERTYKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QWERTYKeyMapTest {
    @Test
    fun japaneseQwertyNumberLayoutPrefersHalfWidthSymbols() {
        assertTrue(QWERTYKeys.NUMBER_KEYS_JP.containsAll(listOf('「', '」', '。', '、')))
        assertFalse(QWERTYKeys.NUMBER_KEYS_JP.contains('['))
        assertFalse(QWERTYKeys.NUMBER_KEYS_JP.contains(']'))
        assertFalse(QWERTYKeys.NUMBER_KEYS_JP.contains('.'))
        assertFalse(QWERTYKeys.NUMBER_KEYS_JP.contains(','))
    }

    @Test
    fun japaneseQwertySymbolLayoutPrefersHalfWidthSymbols() {
        assertTrue(QWERTYKeys.SYMBOL_KEYS_JP.containsAll(listOf('[', ']', '.', ',')))
        assertTrue(QWERTYKeys.SYMBOL_KEYS_JP.contains('・'))
        assertFalse(QWERTYKeys.SYMBOL_KEYS_JP.contains('。'))
        assertFalse(QWERTYKeys.SYMBOL_KEYS_JP.contains('、'))
    }

    @Test
    fun japaneseQwertyKeyMapEmitsHalfWidthSymbols() {
        val keyMap = QWERTYKeyMap()

        assertEquals('「', keyMap.getKeyInfoNumberJP(QWERTYKey.QWERTYKeyJ).tap())
        assertEquals('」', keyMap.getKeyInfoNumberJP(QWERTYKey.QWERTYKeyK).tap())
        assertEquals('。', keyMap.getKeyInfoNumberJP(QWERTYKey.QWERTYKeyZ).tap())
        assertEquals('、', keyMap.getKeyInfoNumberJP(QWERTYKey.QWERTYKeyX).tap())
        assertEquals('·', keyMap.getKeyInfoSymbolJP(QWERTYKey.QWERTYKeyM).tap())
    }

    private fun QWERTYKeyInfo.tap(): Char? {
        return (this as QWERTYKeyInfo.QWERTYVariation).tap
    }
}
