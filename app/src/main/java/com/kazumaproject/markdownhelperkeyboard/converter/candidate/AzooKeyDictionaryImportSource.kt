package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class AzooKeyDictionaryShardSpec(
    val identifier: String,
    val shardIndices: IntRange,
    val sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
)

class AzooKeyDictionaryImportSource(
    private val shardLoader: AzooKeyDictionaryShardLoader,
    private val shardSpecs: List<AzooKeyDictionaryShardSpec>,
) {
    fun loadEntries(): List<AzooKeyDictionaryEntry> {
        return shardSpecs.flatMap { spec ->
            shardLoader.loadLoudstxt3Entries(
                identifier = spec.identifier,
                shardIndices = spec.shardIndices,
                sourceKind = spec.sourceKind,
            )
        }
    }

    fun loadIndex(): AzooKeyDictionaryEntryIndex {
        return AzooKeyDictionaryEntryIndex(loadEntries())
    }
}
