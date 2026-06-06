package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyLoudstxt3BinaryParserTest {
    @Test
    fun parsePayloadReadsRowsAndRubyWordTextArea() {
        val payload = makePayload(
            ruby = "カ",
            rows = listOf(
                Row(word = "蚊", leftId = 1, rightId = 2, mid = 3, score = -1.4f),
                Row(word = "カ", leftId = 4, rightId = 5, mid = 6, score = -2.6f),
            )
        )

        val result = AzooKeyLoudstxt3BinaryParser.parsePayload(payload)

        assertEquals(listOf("蚊", "カ"), result.map { it.surface })
        assertEquals(listOf("カ", "カ"), result.map { it.reading })
        assertEquals(listOf(1, 4), result.map { it.leftId })
        assertEquals(listOf(2, 5), result.map { it.rightId })
        assertEquals(listOf(3, 6), result.map { it.mid })
        assertEquals(listOf(-1, -2), result.map { it.wordCost })
        assertEquals(listOf(-1.4f, -2.6f), result.map { it.value })
    }

    @Test
    fun parseFileReadsHeaderOffsetsAndCanFilterIndices() {
        val bytes = makeFile(
            listOf(
                makePayload("カ", listOf(Row("蚊", 1, 1, 1, -1f))),
                makePayload("キ", listOf(Row("木", 2, 2, 2, -2f))),
            )
        )

        val all = AzooKeyLoudstxt3BinaryParser.parseFile(bytes)
        val filtered = AzooKeyLoudstxt3BinaryParser.parseFile(bytes, indices = setOf(1))

        assertEquals(listOf("蚊", "木"), all.map { it.surface })
        assertEquals(listOf("木"), filtered.map { it.surface })
    }

    @Test
    fun parseRejectsTruncatedData() {
        assertTrue(AzooKeyLoudstxt3BinaryParser.parseFile(byteArrayOf(1)).isEmpty())
        assertTrue(AzooKeyLoudstxt3BinaryParser.parsePayload(byteArrayOf(1)).isEmpty())
    }

    private data class Row(
        val word: String,
        val leftId: Int,
        val rightId: Int,
        val mid: Int,
        val score: Float,
    )

    private fun makeFile(payloads: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeUInt16LE(payloads.size)
        var offset = 2 + payloads.size * 4
        payloads.forEach { payload ->
            out.writeUInt32LE(offset)
            offset += payload.size
        }
        payloads.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun makePayload(
        ruby: String,
        rows: List<Row>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeUInt16LE(rows.size)
        rows.forEach { row ->
            out.writeUInt16LE(row.leftId)
            out.writeUInt16LE(row.rightId)
            out.writeUInt16LE(row.mid)
            out.writeFloat32LE(row.score)
        }
        val text = (listOf(ruby) + rows.map { if (it.word == ruby) "" else it.word }).joinToString("\t")
        out.write(text.toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeUInt16LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeUInt32LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeFloat32LE(value: Float) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array())
    }
}
