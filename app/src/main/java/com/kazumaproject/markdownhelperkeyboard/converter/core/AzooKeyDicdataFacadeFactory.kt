package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryAssetProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionaryRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AzooKeyDicdataFacadeFactory @Inject constructor(
    private val dictionaryAssets: AzooKeyDictionaryAssetProvider,
) {
    private val searchMemoryRef = AtomicReference<suspend (String, Int) -> List<AzooKeyDictionaryEntry>>(
        { _, _ -> emptyList() },
    )

    private val sharedFacade: AzooKeyDicdataFacade by lazy {
        val registry = dictionaryAssets.loudsDictionaryRegistry
            ?: error("AzooKey LOUDS registry is required for conversion")
        AzooKeyDicdataFacade(
            loudsLookups = listOf(registry),
            searchMemory = { reading, limit -> searchMemoryRef.get()(reading, limit) },
            typoSearchers = emptyList(),
            connectionStore = dictionaryAssets.connectionCostStore,
        )
    }

    fun create(
        searchMemory: suspend (reading: String, limit: Int) -> List<AzooKeyDictionaryEntry>,
    ): AzooKeyDicdataFacade {
        searchMemoryRef.set(searchMemory)
        return sharedFacade
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
            typoSearchers = emptyList(),
            connectionStore = connectionStore,
        )
    }
}
