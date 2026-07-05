package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyLearningMemoryPersistenceTest {
    @Test
    fun persistCreatesMemoryLoudsFiles() {
        val dir = File.createTempFile("memory-test", "").apply {
            delete()
            mkdirs()
        }
        val charIdMap = bundledCharIdMap()
        val entries = listOf(
            AzooKeyDictionaryEntryMapper.memory(
                surface = "司会",
                reading = "しかい",
                leftId = 1285,
                rightId = 1285,
                legacyScore = 100,
                readingLength = 3,
            ),
        )
        assertTrue(
            AzooKeyLearningMemoryPersistence.persist(
                directory = dir,
                entries = entries,
                charIdMap = charIdMap,
            )
        )
        assertTrue(
            File(dir, "memory.louds").isFile &&
                File(dir, "memory.loudschars2").isFile &&
                File(dir, "memory0.loudstxt3").isFile
        )

        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = dir.absolutePath,
        )
        val lookup = AzooKeyLoudsDictionaryLookup(
            identifier = "memory",
            charIdMap = charIdMap,
            loudsTrie = loader.loadLoudsTrie("memory")!!,
            shardLoader = loader,
            sourceKind = AzooKeyDictionarySourceKind.Memory,
        )
        val surfaces = lookup.exactEntries("しかい").map { it.surface }
        assertTrue(surfaces.contains("司会"))

        val prefixSurfaces = lookup.prefixEntries("しか", maxDepth = 2, maxCount = 8).map { it.surface }
        assertTrue(prefixSurfaces.contains("司会"))
    }

    @Test
    fun persistTrieDeltaAppendsShardWithoutRewritingEarlierShards() {
        val dir = File.createTempFile("memory-delta", "").apply {
            delete()
            mkdirs()
        }
        val charIdMap = bundledCharIdMap()
        val first = AzooKeyDictionaryEntryMapper.memory(
            surface = "司会",
            reading = "しかい",
            leftId = 1285,
            rightId = 1285,
            legacyScore = 100,
            readingLength = 3,
        )
        val trie = AzooKeyTemporalLearningMemoryTrie.fromEntries(listOf(first), charIdMap)
        assertTrue(AzooKeyLearningMemoryPersistence.persistTrie(dir, trie))
        val firstShard = File(dir, "memory0.loudstxt3")
        val firstShardBytes = firstShard.readBytes()

        val previousGroupCount = AzooKeyLoudsBinaryBuilder.exportFromTrie(trie).nodeRubyGroups.size
        val tokyo = AzooKeyDictionaryEntryMapper.memory(
            surface = "東京",
            reading = "とうきょう",
            leftId = 1285,
            rightId = 1285,
            legacyScore = 80,
            readingLength = 5,
        )
        trie.memorize(tokyo, charIdMap.encode(tokyo.reading)!!.map { it.toByte() })
        val exportAfterInsert = AzooKeyLoudsBinaryBuilder.exportFromTrie(trie)
        AzooKeyLearningMemoryPersistence.persistTrieDelta(
            directory = dir,
            trie = trie,
            previousRubyGroupCount = previousGroupCount,
            export = exportAfterInsert,
        )
        assertTrue(firstShardBytes.contentEquals(firstShard.readBytes()))
        assertTrue(AzooKeyLearningMemoryPersistence.shardCount(dir) >= 1)
    }

    @Test
    fun persistTrieRewritesShardsOnUpsert() {
        val dir = File.createTempFile("memory-upsert", "").apply {
            delete()
            mkdirs()
        }
        val charIdMap = bundledCharIdMap()
        val trie = AzooKeyTemporalLearningMemoryTrie.fromEntries(
            listOf(
                AzooKeyDictionaryEntryMapper.memory(
                    surface = "司会",
                    reading = "しかい",
                    leftId = 1285,
                    rightId = 1285,
                    legacyScore = 100,
                    readingLength = 3,
                ),
            ),
            charIdMap,
        )
        assertTrue(AzooKeyLearningMemoryPersistence.persistTrie(dir, trie))
        val updated = AzooKeyDictionaryEntryMapper.memory(
            surface = "司会",
            reading = "しかい",
            leftId = 1285,
            rightId = 1285,
            legacyScore = 9999,
            readingLength = 3,
        )
        assertFalse(trie.memorize(updated, charIdMap.encode("しかい")!!.map { it.toByte() }))
        assertTrue(AzooKeyLearningMemoryPersistence.persistTrie(dir, trie))

        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = dir.absolutePath,
        )
        val lookup = AzooKeyLoudsDictionaryLookup(
            identifier = "memory",
            charIdMap = charIdMap,
            loudsTrie = loader.loadLoudsTrie("memory")!!,
            shardLoader = loader,
            sourceKind = AzooKeyDictionarySourceKind.Memory,
        )
        val entry = lookup.exactEntries("しかい").first { it.surface == "司会" }
        assertTrue(entry.value < -1f)
    }

    @Test
    fun persistTrieRewritesShardsOnSecondSurfaceSameReading() {
        val dir = File.createTempFile("memory-homograph", "").apply {
            delete()
            mkdirs()
        }
        val charIdMap = bundledCharIdMap()
        val charIds = charIdMap.encode("しかい")!!.map { it.toByte() }
        val trie = AzooKeyTemporalLearningMemoryTrie.fromEntries(
            listOf(
                AzooKeyDictionaryEntryMapper.memory(
                    surface = "司会",
                    reading = "しかい",
                    leftId = 1285,
                    rightId = 1285,
                    legacyScore = 100,
                    readingLength = 3,
                ),
            ),
            charIdMap,
        )
        assertTrue(AzooKeyLearningMemoryPersistence.persistTrie(dir, trie))
        val previousGroupCount = AzooKeyLoudsBinaryBuilder.exportFromTrie(trie).nodeRubyGroups.size
        val shikaiTrial = AzooKeyDictionaryEntryMapper.memory(
            surface = "試会",
            reading = "しかい",
            leftId = 1285,
            rightId = 1285,
            legacyScore = 90,
            readingLength = 3,
        )
        assertTrue(trie.memorize(shikaiTrial, charIds))
        val export = AzooKeyLoudsBinaryBuilder.exportFromTrie(trie)
        assertEquals(previousGroupCount, export.nodeRubyGroups.size)
        assertTrue(AzooKeyLearningMemoryPersistence.persistTrie(dir, trie))

        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = dir.absolutePath,
        )
        val lookup = AzooKeyLoudsDictionaryLookup(
            identifier = "memory",
            charIdMap = charIdMap,
            loudsTrie = loader.loadLoudsTrie("memory")!!,
            shardLoader = loader,
            sourceKind = AzooKeyDictionarySourceKind.Memory,
        )
        val surfaces = lookup.exactEntries("しかい").map { it.surface }.toSet()
        assertTrue(surfaces.containsAll(setOf("司会", "試会")))
    }

    private fun bundledCharIdMap(): AzooKeyCharIdMap {
        val file = listOf(
            File("app/src/main/assets/louds/charID.chid"),
            File("src/main/assets/louds/charID.chid"),
        ).firstOrNull { it.isFile }
            ?: error("charID.chid is missing")
        return AzooKeyCharIdMap.parse(file.readText())
    }
}