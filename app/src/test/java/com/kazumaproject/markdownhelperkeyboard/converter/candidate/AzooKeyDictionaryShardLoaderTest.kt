package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyDictionaryShardLoaderTest {
    @Test
    fun loadTextShardEntriesUsesEscapedAzooKeyPath() {
        val requestedPaths = mutableListOf<String>()
        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path ->
                requestedPaths += path
                """
                ア	亜	1285	1285	501	-8
                アイ	愛	1285	1285	501	-10
                """.trimIndent().toByteArray(Charsets.UTF_8)
            }
        )

        val result = loader.loadLoudstxt3ShardEntries(identifier = "ア", shardIndex = 0)

        assertEquals(listOf("louds/[30A2]0.loudstxt3"), requestedPaths)
        assertEquals(listOf("亜", "愛"), result.map { it.surface })
    }

    @Test
    fun loadBinaryShardEntriesParsesAzooKeyLoudstxt3Binary() {
        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path ->
                if (path == "Dictionary/[3042]0.loudstxt3") {
                    makeFile(
                        listOf(
                            makePayload("ア", listOf(Row("亜", 1, 1, 1, -1f))),
                            makePayload("アイ", listOf(Row("愛", 2, 2, 2, -2f))),
                        )
                    )
                } else {
                    null
                }
            },
            loudsDirectory = "Dictionary",
        )

        val result = loader.loadLoudstxt3ShardEntries(identifier = "あ", shardIndex = 0)

        assertEquals(listOf("亜", "愛"), result.map { it.surface })
    }

    @Test
    fun loadIndexBuildsSearchableEntryIndexAcrossShards() {
        val files = mapOf(
            "louds/[30A2]0.loudstxt3" to "ア\t亜\t1285\t1285\t501\t-8\n".toByteArray(Charsets.UTF_8),
            "louds/[30A2]1.loudstxt3" to "アイ\t愛\t1285\t1285\t501\t-10\n".toByteArray(Charsets.UTF_8),
        )
        val loader = AzooKeyDictionaryShardLoader(readBytes = files::get)

        val index = loader.loadIndex(identifier = "ア", shardIndices = 0..1)

        assertEquals(listOf("亜", "愛"), index.searchPrefix("ア").map { it.surface })
    }

    @Test
    fun loadIdentifierManifestParsesConfiguredLoudsIdentifiers() {
        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path ->
                if (path == "louds/identifiers.txt") {
                    "ア イ\n# comment\nuser".toByteArray(Charsets.UTF_8)
                } else {
                    null
                }
            }
        )

        assertEquals(setOf("ア", "イ", "user"), loader.loadIdentifierManifest())
    }

    @Test
    fun loadLoudsDictionaryRegistryReturnsNullWhenManifestIsMissing() {
        val loader = AzooKeyDictionaryShardLoader(readBytes = { null })

        assertEquals(null, loader.loadLoudsDictionaryRegistry())
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
