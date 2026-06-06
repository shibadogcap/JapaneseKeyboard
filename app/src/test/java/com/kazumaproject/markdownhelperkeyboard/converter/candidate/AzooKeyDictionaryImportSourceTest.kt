package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyDictionaryImportSourceTest {
    @Test
    fun loadEntriesCombinesMultipleShardSpecsWithSourceKind() {
        val files = mapOf(
            "louds/[30A2]0.loudstxt3" to "ア\t亜\t1285\t1285\t501\t-8\n".toByteArray(Charsets.UTF_8),
            "louds/[30A4]0.loudstxt3" to "イ\t胃\t1285\t1285\t501\t-9\n".toByteArray(Charsets.UTF_8),
            "louds/user0.loudstxt3" to "ユーザー\tuser\t1288\t1288\t501\t-5\n".toByteArray(Charsets.UTF_8),
        )
        val source = AzooKeyDictionaryImportSource(
            shardLoader = AzooKeyDictionaryShardLoader(readBytes = files::get),
            shardSpecs = listOf(
                AzooKeyDictionaryShardSpec("ア", 0..0, AzooKeyDictionarySourceKind.System),
                AzooKeyDictionaryShardSpec("イ", 0..0, AzooKeyDictionarySourceKind.System),
                AzooKeyDictionaryShardSpec("user", 0..0, AzooKeyDictionarySourceKind.User),
            ),
        )

        val entries = source.loadEntries()

        assertEquals(listOf("亜", "胃", "user"), entries.map { it.surface })
        assertEquals(
            listOf(
                AzooKeyDictionarySourceKind.System,
                AzooKeyDictionarySourceKind.System,
                AzooKeyDictionarySourceKind.User,
            ),
            entries.map { it.sourceKind },
        )
    }

    @Test
    fun loadIndexMakesImportedShardsSearchable() {
        val files = mapOf(
            "louds/[30A2]0.loudstxt3" to "ア\t亜\t1285\t1285\t501\t-8\nアイ\t愛\t1285\t1285\t501\t-10\n".toByteArray(Charsets.UTF_8),
        )
        val source = AzooKeyDictionaryImportSource(
            shardLoader = AzooKeyDictionaryShardLoader(readBytes = files::get),
            shardSpecs = listOf(AzooKeyDictionaryShardSpec("ア", 0..0)),
        )

        val index = source.loadIndex()

        assertEquals(listOf("亜", "愛"), index.searchPrefix("ア").map { it.surface })
    }
}
