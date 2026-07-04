package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyParityGoldenFixtures
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AzooKeyLoudsMovingTowardHelperTest {
    @Test
    fun helperAccumulatesMultipleDepths() {
        assumeTrue(File("app/src/main/assets/azookey/cb/1285.binary").isFile || File("src/main/assets/azookey/cb/1285.binary").isFile)
        val registry = AzooKeyParityGoldenFixtures.registry()
        val searcher = registry.trieTypoSearcherForIdentifier("ツ") ?: error("missing trie")
        val charIdMap = registry.charIdMap() ?: error("missing charIdMap")
        val helper = AzooKeyLoudsMovingTowardPrefixSearchHelper(searcher.trie)
        val katakana = "ツカッテイル"
        val charIds = charIdMap.encode(katakana) ?: error("encode failed")
        val result = helper.update(charIds)
        println("updated=${result.updated} max=${result.availableMaxIndex} depths=${helper.indicesInDepth(0..100).size}")
        assertTrue(result.updated)
        assertTrue("availableMaxIndex=${result.availableMaxIndex}", result.availableMaxIndex >= katakana.length - 1)
        val entries = registry.entriesForIdentifier("ツ", helper.indicesInDepth(0..100))
        val readings = entries.map { it.reading }.distinct().sorted()
        println("readings=$readings")
        assertTrue(readings.any { it.startsWith("ツカ") })
    }

    @Test
    fun helperIncrementalPrefixUpdates() {
        assumeTrue(File("app/src/main/assets/azookey/cb/1285.binary").isFile || File("src/main/assets/azookey/cb/1285.binary").isFile)
        val registry = AzooKeyParityGoldenFixtures.registry()
        val searcher = registry.trieTypoSearcherForIdentifier("ツ") ?: error("missing trie")
        val charIdMap = registry.charIdMap() ?: error("missing charIdMap")
        val helper = AzooKeyLoudsMovingTowardPrefixSearchHelper(searcher.trie)
        val katakana = "ツカッテイル"
        for (len in 1..katakana.length) {
            val prefix = katakana.substring(0, len)
            val charIds = charIdMap.encode(prefix) ?: error("encode failed")
            val result = helper.update(charIds)
            assertTrue("prefix=$prefix max=${result.availableMaxIndex}", result.availableMaxIndex == len - 1)
        }
        val depths = (0..100).filter { helper.indicesInDepth(it..it).isNotEmpty() }
        assertTrue("depths=$depths", depths.size >= 3)
    }

    @Test
    fun motteikuTypoPrefixUsesMoTrie() {
        assumeTrue(File("app/src/main/assets/azookey/cb/1285.binary").isFile || File("src/main/assets/azookey/cb/1285.binary").isFile)
        val registry = AzooKeyParityGoldenFixtures.registry()
        val charIdMap = registry.charIdMap() ?: error("missing charIdMap")
        val moTrie = registry.trieTypoSearcherForIdentifier("モ") ?: error("missing モ trie")
        val helper = AzooKeyLoudsMovingTowardPrefixSearchHelper(moTrie.trie)
        val motteiku = charIdMap.encode("モッテイク") ?: error("encode failed")
        val motsuteigu = charIdMap.encode("モツテイグ") ?: error("encode failed")
        val motte = helper.update(motteiku)
        helper.indicesInDepth(0..100) // reset by new helper for second
        val helper2 = AzooKeyLoudsMovingTowardPrefixSearchHelper(moTrie.trie)
        val motsu = helper2.update(motsuteigu)
        assertTrue("motte=$motte motsu=$motsu", motte.updated && motte.availableMaxIndex >= 4)
        assertTrue(motsu.availableMaxIndex >= 1)
    }

    @Test
    fun prefixEntriesFindTsuka() {
        assumeTrue(File("app/src/main/assets/azookey/cb/1285.binary").isFile || File("src/main/assets/azookey/cb/1285.binary").isFile)
        val registry = AzooKeyParityGoldenFixtures.registry()
        val entries = registry.commonPrefixEntries("ツカッテイル")
        val readings = entries.map { it.reading to it.surface }.distinct().take(30)
        println("commonPrefix=$readings")
        assertTrue(entries.any { it.reading.startsWith("ツカ") })
    }
}
