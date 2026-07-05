package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryAssetProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionaryRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AzooKeyDicdataFacadeFactory @Inject constructor(
    private val dictionaryAssets: AzooKeyDictionaryAssetProvider,
) {
    fun create(
        searchMemory: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
    ): AzooKeyDicdataFacade {
        val registry = dictionaryAssets.loudsDictionaryRegistry
            ?: error("AzooKey LOUDS registry is required for conversion")
        return AzooKeyDicdataFacade(
            loudsLookups = listOf(registry),
            searchMemory = searchMemory,
            typoSearchers = registry.typoSearchers(),
            connectionStore = dictionaryAssets.connectionCostStore,
        )
    }

    fun connectionStore(): AzooKeyConnectionCostStore? = dictionaryAssets.connectionCostStore
}

internal fun interface AzooKeyDicdataFacadeSource {
    fun create(
        searchMemory: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
    ): AzooKeyDicdataFacade
}

internal fun AzooKeyDicdataFacadeFactory.asSource(): AzooKeyDicdataFacadeSource {
    return AzooKeyDicdataFacadeSource { searchMemory -> create(searchMemory) }
}

internal fun azooKeyDicdataFacadeSourceForTests(
    registry: AzooKeyLoudsDictionaryRegistry,
    connectionStore: AzooKeyConnectionCostStore,
): AzooKeyDicdataFacadeSource {
    return AzooKeyDicdataFacadeSource { searchMemory ->
        AzooKeyDicdataFacade(
            loudsLookups = listOf(registry),
            searchMemory = searchMemory,
            typoSearchers = registry.typoSearchers(),
            connectionStore = connectionStore,
        )
    }
}
