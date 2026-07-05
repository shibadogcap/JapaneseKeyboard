package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyLoudsDictionaryLookupTest {
    @Test
    fun exactEntriesLoadsOnlyNodeIndexSlotFromAlignedShard() {
        val lookup = makeLookup()

        val result = lookup.exactEntries("カ")

        assertEquals(listOf("蚊"), result.map { it.surface })
        assertEquals(listOf("カ"), result.map { it.reading })
    }

    @Test
    fun prefixEntriesLoadsDescendantNodeSlotsAcrossShards() {
        val lookup = makeLookup()

        val result = lookup.prefixEntries("カ")

        assertEquals(listOf("架空"), result.map { it.surface })
        assertEquals(listOf("カク"), result.map { it.reading })
    }

    private fun makeLookup(): AzooKeyLoudsDictionaryLookup {
        val files = mapOf(
            "louds/[30AB]0.loudstxt3" to makeFile(
                listOf(
                    emptyPayload(),
                    emptyPayload(),
                    makePayload("カ", listOf(Row("蚊", 1, 1, 501, -1f))),
                    makePayload("キ", listOf(Row("木", 1, 1, 501, -2f))),
                )
            ),
            "louds/[30AB]1.loudstxt3" to makeFile(
                listOf(
                    makePayload("カク", listOf(Row("架空", 1, 1, 501, -3f))),
                    emptyPayload(),
                    emptyPayload(),
                    emptyPayload(),
                )
            ),
        )
        return AzooKeyLoudsDictionaryLookup(
            identifier = "カ",
            charIdMap = AzooKeyCharIdMap.parse("カキク"),
            loudsTrie = AzooKeyLoudsTrie.fromAzooKeyBinary(
                loudsBytes = makeLoudsBytes(listOf(true, false, true, true, false, true, false, false, false)),
                loudsChars2Bytes = byteArrayOf(0, 0, 0, 1, 2),
            ),
            shardLoader = AzooKeyDictionaryShardLoader(readBytes = files::get),
            shardShift = 2,
        )
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

    private fun emptyPayload(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeUInt16LE(0)
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

    private fun ByteArrayOutputStream.writeLongLE(value: Long) {
        for (index in 0 until Long.SIZE_BYTES) {
            write(((value ushr (index * 8)) and 0xffL).toInt())
        }
    }
}
