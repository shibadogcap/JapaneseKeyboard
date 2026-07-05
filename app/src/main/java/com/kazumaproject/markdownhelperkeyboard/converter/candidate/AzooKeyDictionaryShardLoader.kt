package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.util.concurrent.ConcurrentHashMap

class AzooKeyDictionaryShardLoader(
    private val readBytes: (path: String) -> ByteArray?,
    private val loudsDirectory: String = "louds",
) {
    private val rawBytesCache = ConcurrentHashMap<String, ByteArray>()
    private val textShardCache = ConcurrentHashMap<String, List<AzooKeyDictionaryEntry>>()
    private val binaryPayloadCache = ConcurrentHashMap<String, List<AzooKeyDictionaryEntry>>()

    private fun readBytesCached(path: String): ByteArray? {
        val cached = rawBytesCache[path]
        if (cached != null) return cached
        val bytes = readBytes(path) ?: return null
        rawBytesCache[path] = bytes
        return bytes
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
    fun loadLoudstxt3Entries(
        identifier: String,
        shardIndices: IntRange,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
    ): List<AzooKeyDictionaryEntry> {
        return shardIndices.flatMap { shardIndex ->
            loadLoudstxt3ShardEntries(
                identifier = identifier,
                shardIndex = shardIndex,
                sourceKind = sourceKind,
            )
        }
    }

    fun loadLoudstxt3ShardEntries(
        identifier: String,
        shardIndex: Int,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
        indices: Set<Int>? = null,
    ): List<AzooKeyDictionaryEntry> {
        val bytes = readFirst(
            AzooKeyDictionaryShardName.loudstxt3FileName(identifier, shardIndex),
            AzooKeyDictionaryShardName.rawLoudstxt3FileName(identifier, shardIndex),
        ) ?: return emptyList()
        return if (looksLikeBinaryLoudstxt3(bytes)) {
            val count = if (bytes.size >= 2) {
                (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
            } else 0
            if (count == 0) return emptyList()

            val selectedIndices = indices ?: (0 until count).toSet()
            selectedIndices.flatMap { index ->
                if (index !in 0 until count) return@flatMap emptyList()
                val cacheKey = "$identifier/$shardIndex/$index/$sourceKind"
                binaryPayloadCache.getOrPut(cacheKey) {
                    val start = bytes.readUInt32LE(2 + index * 4)
                    val end = if (index == count - 1) {
                        bytes.size
                    } else {
                        bytes.readUInt32LE(2 + (index + 1) * 4)
                    }
                    val headerSize = 2 + count * 4
                    if (start !in headerSize..bytes.size || end !in start..bytes.size) {
                        emptyList()
                    } else {
                        AzooKeyLoudstxt3BinaryParser.parsePayload(
                            bytes = bytes.copyOfRange(start, end),
                            sourceKind = sourceKind,
                        )
                    }
                }
            }
        } else {
            val cacheKey = "$identifier/$shardIndex/$sourceKind"
            val entries = textShardCache.getOrPut(cacheKey) {
                AzooKeyDicdataElementTextParser.parseLines(
                    text = bytes.toString(Charsets.UTF_8),
                    sourceKind = sourceKind,
                )
            }
            if (indices == null) entries else entries.filterIndexed { index, _ -> index in indices }
        }
    }

    fun loadIndex(
        identifier: String,
        shardIndices: IntRange,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
    ): AzooKeyDictionaryEntryIndex {
        return AzooKeyDictionaryEntryIndex(
            loadLoudstxt3Entries(
                identifier = identifier,
                shardIndices = shardIndices,
                sourceKind = sourceKind,
            )
        )
    }

    fun loadCharIdMap(): AzooKeyCharIdMap? {
        val bytes = readBytesCached("$loudsDirectory/charID.chid") ?: return null
        return AzooKeyCharIdMap.parse(bytes.toString(Charsets.UTF_8))
    }

    fun loadIdentifierManifest(
        fileName: String = AzooKeyLoudsIdentifierManifest.DefaultFileName,
    ): Set<String> {
        val bytes = readBytesCached("$loudsDirectory/$fileName") ?: return emptySet()
        return AzooKeyLoudsIdentifierManifest.parse(bytes.toString(Charsets.UTF_8))
    }

    fun loadLoudsTrie(identifier: String): AzooKeyLoudsTrie? {
        val loudsBytes = readFirst(
            AzooKeyDictionaryShardName.loudsFileName(identifier),
            AzooKeyDictionaryShardName.rawLoudsFileName(identifier),
        )
            ?: return null
        val loudsChars2Bytes = readFirst(
            AzooKeyDictionaryShardName.loudsChars2FileName(identifier),
            AzooKeyDictionaryShardName.rawLoudsChars2FileName(identifier),
        )
            ?: return null
        return AzooKeyLoudsTrie.fromAzooKeyBinary(
            loudsBytes = loudsBytes,
            loudsChars2Bytes = loudsChars2Bytes,
        )
    }

    fun loadLoudsDictionaryLookup(
        identifier: String,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
        shardShift: Int = AzooKeyLoudsDictionaryLookup.DefaultShardShift,
    ): AzooKeyLoudsDictionaryLookup? {
        val charIdMap = loadCharIdMap() ?: return null
        val loudsTrie = loadLoudsTrie(identifier) ?: return null
        return AzooKeyLoudsDictionaryLookup(
            identifier = identifier,
            charIdMap = charIdMap,
            loudsTrie = loudsTrie,
            shardLoader = this,
            sourceKind = sourceKind,
            shardShift = shardShift,
        )
    }

    fun loadLoudsDictionaryRegistry(
        identifiers: Set<String> = loadIdentifierManifest(),
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
        shardShift: Int = AzooKeyLoudsDictionaryLookup.DefaultShardShift,
    ): AzooKeyLoudsDictionaryRegistry? {
        if (identifiers.isEmpty()) {
            return null
        }
        return AzooKeyLoudsDictionaryRegistry(
            loader = this,
            identifiers = identifiers,
            sourceKind = sourceKind,
            shardShift = shardShift,
        )
    }

    private fun readFirst(vararg fileNames: String): ByteArray? {
        return fileNames
            .distinct()
            .firstNotNullOfOrNull { fileName -> readBytesCached("$loudsDirectory/$fileName") }
    }

    private fun looksLikeBinaryLoudstxt3(bytes: ByteArray): Boolean {
        if (bytes.size < 6) {
            return false
        }
        val count = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        if (count <= 0) {
            return false
        }
        val headerSize = 2 + count * 4
        if (headerSize > bytes.size) {
            return false
        }
        val firstOffset = (bytes[2].toInt() and 0xFF) or
                ((bytes[3].toInt() and 0xFF) shl 8) or
                ((bytes[4].toInt() and 0xFF) shl 16) or
                ((bytes[5].toInt() and 0xFF) shl 24)
        return firstOffset >= headerSize && firstOffset <= bytes.size
    }
}
