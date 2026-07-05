package com.kazumaproject.markdownhelperkeyboard.converter.candidate

class AzooKeyLoudsTrie private constructor(
    private val words: LongArray,
    private val nodeIndexToCharId: IntArray,
) {
    private val nodeIndicesByCharId: Array<IntArray> = buildNodeIndicesByCharId(nodeIndexToCharId)
    private val zerosBeforeWord: IntArray = buildZerosBeforeWord(words)
    private val zeroPositions: IntArray = buildZeroPositions(words, zerosBeforeWord.lastOrNull() ?: 0)

    fun childNodeIndices(parentNodeIndex: Int): IntRange {
        if (parentNodeIndex < 1) return IntRange.EMPTY
        val currentZeroPosition = selectZero(parentNodeIndex)
        val nextZeroPosition = selectZero(parentNodeIndex + 1)
        if (currentZeroPosition < 0 || nextZeroPosition < 0) return IntRange.EMPTY
        val start = currentZeroPosition - parentNodeIndex + 2
        val endExclusive = nextZeroPosition - parentNodeIndex + 1
        if (start >= endExclusive) return IntRange.EMPTY
        return start until endExclusive
    }

    fun searchCharNodeIndex(parentNodeIndex: Int, charId: Int): Int? {
        if (charId !in 0..255) return null
        val childNodeIndices = childNodeIndices(parentNodeIndex)
        if (childNodeIndices.isEmpty()) return null
        val nodes = nodeIndicesByCharId[charId]
        val start = lowerBound(nodes, childNodeIndices.first)
        return nodes
            .getOrNull(start)
            ?.takeIf { it in childNodeIndices }
    }

    fun searchNodeIndex(charIds: List<Int>): Int? {
        var nodeIndex = RootNodeIndex
        for (charId in charIds) {
            nodeIndex = searchCharNodeIndex(nodeIndex, charId) ?: return null
        }
        return nodeIndex
    }

    fun commonPrefixNodeIndices(charIds: List<Int>): List<Int> {
        val result = mutableListOf<Int>()
        var nodeIndex = RootNodeIndex
        for (charId in charIds) {
            nodeIndex = searchCharNodeIndex(nodeIndex, charId) ?: break
            result.add(nodeIndex)
        }
        return result
    }

    fun prefixNodeIndices(
        charIds: List<Int>,
        maxDepth: Int = Int.MAX_VALUE,
        maxCount: Int = Int.MAX_VALUE,
    ): List<Int> {
        val nodeIndex = searchNodeIndex(charIds) ?: return emptyList()
        return collectPrefixNodeIndices(
            nodeIndex = nodeIndex,
            depth = 0,
            maxDepth = maxDepth,
            maxCount = maxCount,
        )
    }

    private fun collectPrefixNodeIndices(
        nodeIndex: Int,
        depth: Int,
        maxDepth: Int,
        maxCount: Int,
    ): List<Int> {
        if (maxCount <= 0) return emptyList()
        val result = childNodeIndices(nodeIndex).toMutableList()
        if (depth == maxDepth) return result.take(maxCount)
        var index = 0
        while (index < result.size && result.size < maxCount) {
            val child = result[index]
            val remaining = maxCount - result.size
            result += collectPrefixNodeIndices(
                nodeIndex = child,
                depth = depth + 1,
                maxDepth = maxDepth,
                maxCount = remaining,
            )
            index++
        }
        return result.take(maxCount)
    }

    private fun selectZero(target: Int): Int {
        if (target < 1 || target >= zeroPositions.size) return -1
        return zeroPositions[target]
    }

    private fun bitAt(word: Long, bitIndex: Int): Boolean =
        ((word ushr (BitsPerWord - bitIndex - 1)) and 1L) == 1L

    companion object {
        private const val BitsPerWord = 64
        private const val RootNodeIndex = 1

        fun fromAzooKeyBinary(loudsBytes: ByteArray, loudsChars2Bytes: ByteArray): AzooKeyLoudsTrie =
            AzooKeyLoudsTrie(
                words = loudsBytes.toLittleEndianLongArray(),
                nodeIndexToCharId = loudsChars2Bytes.map { it.toInt() and 0xff }.toIntArray(),
            )

        private fun ByteArray.toLittleEndianLongArray(): LongArray {
            val count = size / Long.SIZE_BYTES
            return LongArray(count) { wordIndex ->
                var value = 0L
                val offset = wordIndex * Long.SIZE_BYTES
                for (byteIndex in 0 until Long.SIZE_BYTES) {
                    value = value or ((this[offset + byteIndex].toLong() and 0xffL) shl (byteIndex * 8))
                }
                value
            }
        }

        private fun buildNodeIndicesByCharId(nodeIndexToCharId: IntArray): Array<IntArray> {
            val buckets = Array(256) { mutableListOf<Int>() }
            for ((nodeIndex, charId) in nodeIndexToCharId.withIndex()) {
                if (charId in buckets.indices) {
                    buckets[charId] += nodeIndex
                }
            }
            return Array(256) { index -> buckets[index].toIntArray() }
        }

        private fun buildZerosBeforeWord(words: LongArray): IntArray {
            val zerosBeforeWord = IntArray(words.size + 1)
            for (index in words.indices) {
                zerosBeforeWord[index + 1] = zerosBeforeWord[index] + (BitsPerWord - words[index].countOneBits())
            }
            return zerosBeforeWord
        }

        private fun buildZeroPositions(words: LongArray, totalZeros: Int): IntArray {
            val positions = IntArray(totalZeros + 1)
            var zeroCount = 0
            for (wordIndex in words.indices) {
                val word = words[wordIndex]
                for (bitIndex in 0 until BitsPerWord) {
                    if (((word ushr (BitsPerWord - bitIndex - 1)) and 1L) == 0L) {
                        zeroCount++
                        if (zeroCount <= totalZeros) {
                            positions[zeroCount] = wordIndex * BitsPerWord + bitIndex
                        }
                    }
                }
            }
            return positions
        }

        private fun lowerBound(values: IntArray, target: Int): Int {
            var low = 0
            var high = values.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (values[mid] < target) {
                    low = mid + 1
                } else {
                    high = mid
                }
            }
            return low
        }
    }
}
