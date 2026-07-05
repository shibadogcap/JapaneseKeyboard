package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AzooKeyLoudsTrieTest {
    @Test
    fun searchNodeIndexWalksAzooKeyLoudsBinary() {
        val trie = AzooKeyLoudsTrie.fromAzooKeyBinary(
            loudsBytes = makeLoudsBytes(listOf(true, false, true, true, false, true, false, false, false)),
            loudsChars2Bytes = byteArrayOf(0, 0, 1, 2, 3),
        )

        assertEquals(2, trie.searchNodeIndex(listOf(1)))
        assertEquals(3, trie.searchNodeIndex(listOf(2)))
        assertEquals(4, trie.searchNodeIndex(listOf(1, 3)))
        assertNull(trie.searchNodeIndex(listOf(3)))
    }

    @Test
    fun prefixNodeIndicesReturnsDescendantsFromMatchedNode() {
        val trie = AzooKeyLoudsTrie.fromAzooKeyBinary(
            loudsBytes = makeLoudsBytes(listOf(true, false, true, true, false, true, false, false, false)),
            loudsChars2Bytes = byteArrayOf(0, 0, 1, 2, 3),
        )

        assertEquals(listOf(4), trie.prefixNodeIndices(listOf(1)))
        assertEquals(emptyList<Int>(), trie.prefixNodeIndices(listOf(2)))
    }

    private fun makeLoudsBytes(bits: List<Boolean>): ByteArray {
        val out = ByteArrayOutputStream()
        var value = 0L
        var bitIndex = 0
        for (bit in bits) {
            if (bit) {
                value = value or (1L shl (63 - bitIndex))
            }
            bitIndex++
            if (bitIndex == Long.SIZE_BITS) {
                out.writeLongLE(value)
                value = 0L
                bitIndex = 0
            }
        }
        if (bitIndex != 0) {
            while (bitIndex < Long.SIZE_BITS) {
                value = value or (1L shl (63 - bitIndex))
                bitIndex++
            }
            out.writeLongLE(value)
        }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLongLE(value: Long) {
        for (index in 0 until Long.SIZE_BYTES) {
            write(((value ushr (index * 8)) and 0xffL).toInt())
        }
    }
}
