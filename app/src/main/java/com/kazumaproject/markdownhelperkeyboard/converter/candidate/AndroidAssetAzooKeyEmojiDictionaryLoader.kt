package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import android.content.res.AssetManager
import java.io.FileNotFoundException
import java.io.IOException

object AndroidAssetAzooKeyEmojiDictionaryLoader {
    fun createSearch(
        assets: AssetManager,
        textReplacerFilePath: String = DefaultTextReplacerEmojiDictionaryPath,
        dicdataFilePath: String = DefaultDicdataEmojiDictionaryPath,
    ): AzooKeyEmojiDictionarySearch? {
        val textReplacerText = readAssetText(assets, textReplacerFilePath) ?: return null
        val dicdataText = readAssetText(assets, dicdataFilePath)
        return AzooKeyEmojiDictionarySearch.fromAzooKeyEmojiDictionaryTexts(
            textReplacerText = textReplacerText,
            dicdataText = dicdataText,
        )
    }

    private fun readAssetText(
        assets: AssetManager,
        filePath: String,
    ): String? {
        return try {
            assets.open(filePath).use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    const val DefaultTextReplacerEmojiDictionaryPath: String = "azookey/emoji/emoji_all_E17.0.txt"
    const val DefaultDicdataEmojiDictionaryPath: String = "azookey/emoji/emoji_dict_E17.0.txt"
}
