package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File

/**
 * LOUDS / loudschars2 バイナリ書き出し（AzooKey DictionaryBuilder 相当）。
 */
object AzooKeyLoudsBinaryBuilder {
    data class ExportResult(
        val bits: BooleanArray,
        val nodes2Characters: ByteArray,
        val nodeRubyGroups: List<AzooKeyLoudstxt3BinaryBuilder.RubyGroup>,
    )

    fun exportFromTrie(trie: AzooKeyTemporalLearningMemoryTrie): ExportResult {
        val bits = mutableListOf(true, false)
        val nodes2Characters = mutableListOf(0.toByte(), 0.toByte())
        val nodeRubyGroups = mutableListOf<AzooKeyLoudstxt3BinaryBuilder.RubyGroup>()

        nodeRubyGroups += AzooKeyLoudstxt3BinaryBuilder.RubyGroup(ruby = "", rows = emptyList())
        nodeRubyGroups += AzooKeyLoudstxt3BinaryBuilder.RubyGroup(ruby = "", rows = emptyList())

        var current = trie.rootChildrenSorted()
        bits += List(current.size) { true } + false
        while (current.isNotEmpty()) {
            val next = mutableListOf<Pair<Byte, Int>>()
            current.forEach { (charId, nodeIndex) ->
                nodes2Characters += charId
                nodeRubyGroups += trie.rubyGroupAtNode(nodeIndex)
                val children = trie.childrenSorted(nodeIndex)
                bits += List(children.size) { true } + false
                next += children
            }
            current = next
        }

        return ExportResult(
            bits = bits.toBooleanArray(),
            nodes2Characters = nodes2Characters.toByteArray(),
            nodeRubyGroups = nodeRubyGroups,
        )
    }

    fun writeLoudsFiles(
        directory: File,
        bits: BooleanArray,
        nodes2Characters: ByteArray,
    ) {
        directory.mkdirs()
        File(directory, AzooKeyDictionaryShardName.rawLoudsFileName(MEMORY_IDENTIFIER))
            .writeBytes(makeLoudsBytes(bits))
        File(directory, AzooKeyDictionaryShardName.rawLoudsChars2FileName(MEMORY_IDENTIFIER))
            .writeBytes(nodes2Characters)
    }

    fun makeLoudsBytes(bits: BooleanArray): ByteArray {
        val unit = 64
        val paddedCount = if (bits.size % unit == 0) bits.size else ((bits.size / unit) + 1) * unit
        val wordCount = paddedCount / unit
        val buffer = ByteArray(wordCount * 8)
        var wordIndex = 0
        var value = 0L
        var idxInUnit = 0
        bits.forEach { bit ->
            if (bit) {
                value = value or (1L shl (unit - idxInUnit - 1))
            }
            idxInUnit++
            if (idxInUnit == unit) {
                writeLongLe(buffer, wordIndex * 8, value)
                value = 0L
                idxInUnit = 0
                wordIndex++
            }
        }
        while (idxInUnit < unit) {
            value = value or (1L shl (unit - idxInUnit - 1))
            idxInUnit++
        }
        if (idxInUnit != 0 && wordIndex < wordCount) {
            writeLongLe(buffer, wordIndex * 8, value)
        }
        return buffer
    }

    private fun writeLongLe(bytes: ByteArray, offset: Int, value: Long) {
        for (byteIndex in 0 until 8) {
            bytes[offset + byteIndex] = ((value shr (byteIndex * 8)) and 0xffL).toByte()
        }
    }

    const val MEMORY_IDENTIFIER: String = "memory"
}