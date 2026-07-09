package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLoudsTrieTypoSearcher
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLoudsTypoSearcher

class AzooKeyLoudsDictionaryRegistry(
    private val loader: AzooKeyDictionaryShardLoader,
    private val identifiers: Set<String>,
    private val sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
    private val shardShift: Int = AzooKeyLoudsDictionaryLookup.DefaultShardShift,
) : AzooKeyLoudsDictionarySearcher {
    private val lookups = mutableMapOf<String, AzooKeyLoudsDictionaryLookup?>()

    override fun exactEntries(reading: String): List<AzooKeyDictionaryEntry> {
        val normalizedReading = reading.hiraganaToKatakana()
        val lookup = lookupFor(normalizedReading) ?: return emptyList()
        return lookup.exactEntries(normalizedReading)
    }

    override fun prefixEntries(
        reading: String,
        maxDepth: Int,
        maxCount: Int,
    ): List<AzooKeyDictionaryEntry> {
        val normalizedReading = reading.hiraganaToKatakana()
        val lookup = lookupFor(normalizedReading) ?: return emptyList()
        return lookup.prefixEntries(
            reading = normalizedReading,
            maxDepth = maxDepth,
            maxCount = maxCount,
        )
    }

    override fun commonPrefixEntries(reading: String): List<AzooKeyDictionaryEntry> {
        val normalizedReading = reading.hiraganaToKatakana()
        val lookup = lookupFor(normalizedReading) ?: return emptyList()
        return lookup.commonPrefixEntries(normalizedReading)
    }

    fun typoSearchers(): List<AzooKeyLoudsTypoSearcher> {
        return listOf(AzooKeyClassicTypoCorrection.classicTypoSearcher())
    }

    fun typoSearchersForPrefix(prefix: String): List<AzooKeyLoudsTypoSearcher> {
        if (prefix.isEmpty()) {
            return typoSearchers()
        }
        val identifier = prefix.first().toString()
        val shardSearcher = lookupByIdentifier(identifier)?.typoSearcher()
        return if (shardSearcher != null) {
            listOf(shardSearcher, AzooKeyClassicTypoCorrection.classicTypoSearcher())
        } else {
            typoSearchers()
        }
    }

    fun lookupByIdentifier(identifier: String): AzooKeyLoudsDictionaryLookup? {
        if (identifier !in identifiers) return null
        return lookups.getOrPut(identifier) {
            loader.loadLoudsDictionaryLookup(
                identifier = identifier,
                sourceKind = sourceKind,
                shardShift = shardShift,
            )
        }
    }

    fun prefixEntriesForIdentifier(
        identifier: String,
        reading: String,
        maxDepth: Int = Int.MAX_VALUE,
        maxCount: Int = Int.MAX_VALUE,
    ): List<AzooKeyDictionaryEntry> {
        val normalizedReading = reading.hiraganaToKatakana()
        return lookupByIdentifier(identifier)?.prefixEntries(
            reading = normalizedReading,
            maxDepth = maxDepth,
            maxCount = maxCount,
        ) ?: emptyList()
    }

    fun entriesForIdentifier(identifier: String, nodeIndices: Collection<Int>): List<AzooKeyDictionaryEntry> {
        return lookupByIdentifier(identifier)?.entriesAtNodeIndices(nodeIndices).orEmpty()
    }

    fun trieTypoSearcherForIdentifier(identifier: String): AzooKeyLoudsTrieTypoSearcher? {
        return lookupByIdentifier(identifier)?.trieTypoSearcher()
    }

    fun charIdMap(): AzooKeyCharIdMap? {
        return identifiers.firstNotNullOfOrNull { lookupByIdentifier(it)?.trieTypoSearcher()?.charIdMap }
    }

    fun exactEntriesForIdentifier(identifier: String, reading: String): List<AzooKeyDictionaryEntry> {
        val normalizedReading = reading.hiraganaToKatakana()
        return lookupByIdentifier(identifier)?.exactEntries(normalizedReading) ?: emptyList()
    }

    private fun lookupFor(reading: String): AzooKeyLoudsDictionaryLookup? {
        val identifier = reading.firstOrNull()?.toString() ?: return null
        return lookupByIdentifier(identifier)
    }
}
