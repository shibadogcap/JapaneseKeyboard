package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import android.content.res.AssetManager
import java.io.FileNotFoundException
import java.io.IOException

object AndroidAssetAzooKeyDictionaryShardLoader {
    fun create(
        assets: AssetManager,
        loudsDirectory: String = "louds",
    ): AzooKeyDictionaryShardLoader {
        return AzooKeyDictionaryShardLoader(
            readBytes = { path ->
                try {
                    assets.open(path).use { it.readBytes() }
                } catch (_: FileNotFoundException) {
                    null
                } catch (_: IOException) {
                    null
                }
            },
            loudsDirectory = loudsDirectory,
        )
    }

    fun createRegistry(
        assets: AssetManager,
        identifiers: Set<String>,
        loudsDirectory: String = "louds",
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
        shardShift: Int = AzooKeyLoudsDictionaryLookup.DefaultShardShift,
    ): AzooKeyLoudsDictionaryRegistry {
        return AzooKeyLoudsDictionaryRegistry(
            loader = create(assets = assets, loudsDirectory = loudsDirectory),
            identifiers = identifiers,
            sourceKind = sourceKind,
            shardShift = shardShift,
        )
    }

    fun createRegistryFromManifest(
        assets: AssetManager,
        loudsDirectory: String = "louds",
        manifestFileName: String = AzooKeyLoudsIdentifierManifest.DefaultFileName,
        sourceKind: AzooKeyDictionarySourceKind = AzooKeyDictionarySourceKind.System,
        shardShift: Int = AzooKeyLoudsDictionaryLookup.DefaultShardShift,
    ): AzooKeyLoudsDictionaryRegistry? {
        val loader = create(assets = assets, loudsDirectory = loudsDirectory)
        val manifestIdentifiers = loader.loadIdentifierManifest(fileName = manifestFileName)
        val identifiers = manifestIdentifiers.ifEmpty {
            AzooKeyLoudsAssetManifest.discoverIdentifiers(
                listAssetFileNames(
                    assets = assets,
                    loudsDirectory = loudsDirectory,
                )
            )
        }
        return loader.loadLoudsDictionaryRegistry(
            identifiers = identifiers,
            sourceKind = sourceKind,
            shardShift = shardShift,
        )
    }

    private fun listAssetFileNames(
        assets: AssetManager,
        loudsDirectory: String,
    ): List<String> {
        return try {
            assets.list(loudsDirectory)?.toList().orEmpty()
        } catch (_: IOException) {
            emptyList()
        }
    }
}
