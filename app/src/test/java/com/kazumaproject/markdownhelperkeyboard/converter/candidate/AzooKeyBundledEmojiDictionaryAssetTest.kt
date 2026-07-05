package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyBundledEmojiDictionaryAssetTest {
    @Test
    fun bundledEmojiDictionaryCanBackInputAndPostCommitSearch() {
        val search = bundledEmojiSearch()

        val inputSurfaces = search.searchInputPrefix("えが", limit = 20).map { it.surface }.toSet()
        val postCommitSurfaces = search.searchPostCommit("笑顔", limit = 20).map { it.surface }.toSet()
        val firstInputResult = search.searchInputPrefix("えが", limit = 20).firstOrNull()

        assertTrue("😀️ should be found from prefix $inputSurfaces", "😀️" in inputSurfaces)
        assertTrue("😀️ should be found from exact post commit $postCommitSurfaces", "😀️" in postCommitSurfaces)
        assertTrue(
            "emoji_dict_E17.0.txt should provide the first prefix result: $firstInputResult",
            firstInputResult?.metadata?.contains(AzooKeyDictionaryMetadata.EmojiDicdata) == true,
        )
    }

    @Test
    fun bundledEmojiDicdataCanBackSymbolLikeSpecialCharacters() {
        val search = bundledEmojiSearch()

        val inputSurfaces = search.searchInputPrefix("おんぷ", limit = 30).map { it.surface }.toSet()

        assertTrue("🎵️ should be found from emoji Dicdata symbol-like entries $inputSurfaces", "🎵️" in inputSurfaces)
    }

    private fun bundledEmojiSearch(): AzooKeyEmojiDictionarySearch {
        val textReplacerFile = listOf(
            File("app/src/main/assets/azookey/emoji/emoji_all_E17.0.txt"),
            File("src/main/assets/azookey/emoji/emoji_all_E17.0.txt"),
        ).firstOrNull { it.isFile }
            ?: error("Bundled AzooKey emoji TextReplacer dictionary is missing")
        val dicdataFile = listOf(
            File("app/src/main/assets/azookey/emoji/emoji_dict_E17.0.txt"),
            File("src/main/assets/azookey/emoji/emoji_dict_E17.0.txt"),
        ).firstOrNull { it.isFile }
            ?: error("Bundled AzooKey emoji Dicdata dictionary is missing")
        return AzooKeyEmojiDictionarySearch.fromAzooKeyEmojiDictionaryTexts(
            textReplacerText = textReplacerFile.readText(Charsets.UTF_8),
            dicdataText = dicdataFile.readText(Charsets.UTF_8),
        )
    }
}
