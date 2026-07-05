package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.nio.ByteBuffer
import java.nio.ByteOrder
object AzooKeyLoudstxt3BinaryParser {
    fun parseFile(
        bytes: ByteArray,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
        indices: Set<Int>? = null,
    ): List<AzooKeyDictionaryEntry> {
        if (bytes.size < 2) {
            return emptyList()
        }
        val count = bytes.readUInt16LE(0)
        val headerSize = 2 + count * 4
        if (count == 0 || bytes.size < headerSize) {
            return emptyList()
        }

        val selectedIndices = indices ?: (0 until count).toSet()
        return selectedIndices
            .asSequence()
            .filter { it in 0 until count }
            .flatMap { index ->
                val start = bytes.readUInt32LE(2 + index * 4)
                val end = if (index == count - 1) {
                    bytes.size
                } else {
                    bytes.readUInt32LE(2 + (index + 1) * 4)
                }
                if (start !in headerSize..bytes.size || end !in start..bytes.size) {
                    emptySequence()
                } else {
                    parsePayload(
                        bytes = bytes.copyOfRange(start, end),
                        sourceKind = sourceKind,
                    ).asSequence()
                }
            }
            .toList()
    }

    fun parsePayload(
        bytes: ByteArray,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
    ): List<AzooKeyDictionaryEntry> {
        if (bytes.size < 2) {
            return emptyList()
        }
        val count = bytes.readUInt16LE(0)
        val textStart = 2 + count * NUMERIC_ROW_SIZE_BYTES
        if (count == 0 || bytes.size < textStart) {
            return emptyList()
        }

        val numericRows = (0 until count).map { rowIndex ->
            val offset = 2 + rowIndex * NUMERIC_ROW_SIZE_BYTES
            NumericRow(
                leftId = bytes.readUInt16LE(offset),
                rightId = bytes.readUInt16LE(offset + 2),
                mid = bytes.readUInt16LE(offset + 4),
                value = AzooKeyPValues.clampDictionaryValue(bytes.readFloat32LE(offset + 6)),
            )
        }
        val textFields = bytes
            .copyOfRange(textStart, bytes.size)
            .toString(Charsets.UTF_8)
            .split('\t')
        val ruby = textFields.firstOrNull().orEmpty()
        if (ruby.isBlank()) {
            return emptyList()
        }

        return numericRows.mapIndexed { index, row ->
            val word = textFields.getOrNull(index + 1).orEmpty().ifBlank { ruby }
            AzooKeyDictionaryEntry(
                surface = word,
                reading = ruby,
                leftId = row.leftId,
                rightId = row.rightId,
                mid = row.mid,
                wordCost = row.value.toInt(),
                value = row.value,
                sourceKind = sourceKind,
            )
        }
    }

    private data class NumericRow(
        val leftId: Int,
        val rightId: Int,
        val mid: Int,
        val value: AzooKeyPValue,
    )

    private fun ByteArray.readUInt16LE(offset: Int): Int {
        if (offset + 2 > size) {
            return 0
        }
        return (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.readUInt32LE(offset: Int): Int {
        if (offset + 4 > size) {
            return 0
        }
        return (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8) or
                ((this[offset + 2].toInt() and 0xFF) shl 16) or
                ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun ByteArray.readFloat32LE(offset: Int): Float {
        if (offset + 4 > size) {
            return -30f
        }
        return ByteBuffer
            .wrap(this, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .float
    }

    private const val NUMERIC_ROW_SIZE_BYTES = 10
}
