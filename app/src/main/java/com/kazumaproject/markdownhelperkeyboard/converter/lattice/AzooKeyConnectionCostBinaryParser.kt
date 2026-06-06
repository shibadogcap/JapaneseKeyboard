package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AzooKey [DicdataStore.loadCCLine](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
object AzooKeyConnectionCostBinaryParser {
    const val CID_COUNT: Int = 1319
    const val MID_COUNT: Int = 502
    const val DEFAULT_UNKNOWN_COST: Float = -25f

    fun parseConnectionLine(bytes: ByteArray): FloatArray {
        if (bytes.size < 8) {
            return FloatArray(CID_COUNT) { DEFAULT_UNKNOWN_COST }
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val firstKey = buffer.int
        val firstValue = buffer.float
        require(firstKey == -1) { "cb binary must start with key -1" }
        val line = FloatArray(CID_COUNT) { firstValue }
        while (buffer.remaining() >= 8) {
            val key = buffer.int
            val value = buffer.float
            if (key in 0 until CID_COUNT) {
                line[key] = value
            }
        }
        return line
    }

    fun parseMorphologicalMatrix(bytes: ByteArray, midCount: Int = MID_COUNT): FloatArray {
        val floatCount = bytes.size / Float.SIZE_BYTES
        if (floatCount <= 0) {
            return FloatArray(midCount * midCount)
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(floatCount)
        buffer.asFloatBuffer().get(result)
        return result
    }
}