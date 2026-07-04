package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import android.content.Context
import android.content.res.AssetManager
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily loads AzooKey LOUDS / emoji dictionary assets from the app APK.
 * Shared across conversion engine and IME post-commit prediction.
 */
@Singleton
class AzooKeyDictionaryAssetProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val assets: AssetManager = context.assets

    val loudsDictionaryRegistry: AzooKeyLoudsDictionaryRegistry? by lazy {
        AndroidAssetAzooKeyDictionaryShardLoader.createRegistryFromManifest(assets = assets)
    }

    val emojiDictionarySearch: AzooKeyEmojiDictionarySearch? by lazy {
        AndroidAssetAzooKeyEmojiDictionaryLoader.createSearch(assets = assets)
    }

    val charIdMap: AzooKeyCharIdMap? by lazy {
        runCatching {
            assets.open("louds/charID.chid").use { stream ->
                AzooKeyCharIdMap.parse(stream.bufferedReader().readText())
            }
        }.getOrNull()
    }

    val connectionCostStore: AzooKeyConnectionCostStore? by lazy {
        AzooKeyConnectionCostStore.fromAssets(assets)
    }
}