package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AzooKey [Loudstxt3Builder](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当。
 */
object AzooKeyLoudstxt3BinaryBuilder {
    data class Row(
        val word: String,
        val leftId: Int,
        val rightId: Int,
        val mid: Int,
        val value: Float,
    )

    data class RubyGroup(
        val ruby: String,
        val rows: List<Row>,
    )

    private const val NUMERIC_ROW_SIZE_BYTES = 10

    fun makeBinary(entries: List<RubyGroup>): ByteArray {
        if (entries.isEmpty()) {
            val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putShort(0)
            return buffer.array()
        }
        val payloads = entries.map { entry -> encodePayload(entry) }
        val headerSize = 2 + entries.size * 4
        val totalSize = headerSize + payloads.sumOf { it.size }
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(entries.size.toShort())
        var offset = headerSize
        payloads.forEach { payload ->
            buffer.putInt(offset)
            offset += payload.size
        }
        payloads.forEach { payload -> buffer.put(payload) }
        return buffer.array()
    }

    fun writeFile(file: File, entries: List<RubyGroup>) {
        file.parentFile?.mkdirs()
        file.writeBytes(makeBinary(entries))
    }

    fun writeSequentialShards(
        entries: List<RubyGroup>,
        split: Int,
        fileProvider: (shardIndex: Int) -> File,
    ): Int {
        if (split <= 0 || entries.isEmpty()) return 0
        var fileIndex = 0
        var start = 0
        while (start < entries.size) {
            val end = minOf(start + split, entries.size)
            writeFile(fileProvider(fileIndex), entries.subList(start, end))
            fileIndex++
            start = end
        }
        return fileIndex
    }

    private fun encodePayload(entry: RubyGroup): ByteArray {
        val text = buildString {
            append(entry.ruby)
            entry.rows.forEach { row ->
                append('\t')
                append(if (row.word == entry.ruby) "" else row.word)
            }
        }
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(2 + entry.rows.size * NUMERIC_ROW_SIZE_BYTES + textBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(entry.rows.size.toShort())
        entry.rows.forEach { row ->
            buffer.putShort(row.leftId.toShort())
            buffer.putShort(row.rightId.toShort())
            buffer.putShort(row.mid.toShort())
            buffer.putFloat(row.value)
        }
        buffer.put(textBytes)
        return buffer.array()
    }
}