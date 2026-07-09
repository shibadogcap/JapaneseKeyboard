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

    val textReplacer: AzooKeyTextReplacer by lazy {
        AndroidAssetAzooKeyEmojiDictionaryLoader.createTextReplacer(assets = assets)
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

    /**
     * Preloads LOUDS shards and connection costs so the first keystroke does not pay cold-start IO.
     */
    fun warmUpConversionAssets() {
        val registry = loudsDictionaryRegistry ?: return
        WARMUP_IDENTIFIER_PREFIXES.forEach { prefix ->
            runCatching { registry.lookupByIdentifier(prefix) }
        }
        connectionCostStore?.getConnectionCost(0, 0)
        charIdMap
    }

    companion object {
        /** Common first katakana identifiers (あ行・か行・さ行・と・ん). */
        private val WARMUP_IDENTIFIER_PREFIXES = listOf("あ", "か", "さ", "と", "ん")
    }
}