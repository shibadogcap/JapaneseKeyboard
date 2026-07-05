package com.kazumaproject.markdownhelperkeyboard.converter.candidate

class AzooKeyDictionaryEntryIndex(
    entries: Iterable<AzooKeyDictionaryEntry>,
) {
    private val entriesByReading: Map<String, List<AzooKeyDictionaryEntry>> =
        entries
            .filter { it.reading.isNotBlank() && it.surface.isNotBlank() }
            .groupBy { it.reading }
            .mapValues { (_, values) -> values.sortedForLookup() }

    fun searchExact(
        reading: String,
        sourceKind: AzooKeyDictionarySourceKind? = null,
        limit: Int = Int.MAX_VALUE,
    ): List<AzooKeyDictionaryEntry> {
        if (reading.isBlank() || limit <= 0) {
            return emptyList()
        }
        return entriesByReading[reading].orEmpty()
            .asSequence()
            .filterSourceKind(sourceKind)
            .distinctBy { it.surface }
            .take(limit)
            .toList()
    }

    fun searchPrefix(
        prefix: String,
        sourceKind: AzooKeyDictionarySourceKind? = null,
        limit: Int = Int.MAX_VALUE,
    ): List<AzooKeyDictionaryEntry> {
        if (prefix.isBlank() || limit <= 0) {
            return emptyList()
        }
        return entriesByReading
            .asSequence()
            .filter { (reading, _) -> reading.startsWith(prefix) }
            .flatMap { (_, values) -> values.asSequence() }
            .filterSourceKind(sourceKind)
            .sortedForLookup()
            .distinctBy { it.surface }
            .take(limit)
            .toList()
    }

    private fun Sequence<AzooKeyDictionaryEntry>.filterSourceKind(
        sourceKind: AzooKeyDictionarySourceKind?,
    ): Sequence<AzooKeyDictionaryEntry> {
        return if (sourceKind == null) {
            this
        } else {
            filter { it.sourceKind == sourceKind }
        }
    }

    private fun Iterable<AzooKeyDictionaryEntry>.sortedForLookup(): List<AzooKeyDictionaryEntry> {
        return sortedWith(entryLookupComparator)
    }

    private fun Sequence<AzooKeyDictionaryEntry>.sortedForLookup(): Sequence<AzooKeyDictionaryEntry> {
        return sortedWith(entryLookupComparator)
    }

    private companion object {
        val entryLookupComparator: Comparator<AzooKeyDictionaryEntry> =
            compareByDescending<AzooKeyDictionaryEntry> { it.azooKeyLookupPriority }
                .thenByDescending { it.value }
                .thenBy { it.reading.length }
                .thenBy { it.surface }

        val AzooKeyDictionaryEntry.azooKeyLookupPriority: Int
            get() = if (AzooKeyDictionaryMetadata.EmojiDicdata in metadata) 1 else 0
    }
}
