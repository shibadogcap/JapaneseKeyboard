package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyParityGoldenFixtures
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyDicdataFacade
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AzooKeyMovingTowardPrefixSearchTest {
    @Test
    fun movingTowardFindsTsukauEntries() = runTest {
        assumeTrue(File("app/src/main/assets/azookey/cb/1285.binary").isFile || File("src/main/assets/azookey/cb/1285.binary").isFile)
        val registry = AzooKeyParityGoldenFixtures.registry()
        val charIdMap = registry.charIdMap() ?: error("charIdMap missing")
        val composing = ComposingText.fromConvertTarget("つかっている")
        val surfaceRange = AzooKeyTypoCorrectionGenerator.ProcessRange(
            leftIndex = 0,
            rightRangeStart = 0,
            rightRangeEndExclusive = composing.convertTarget.length.coerceAtMost(20),
        )
        val result = AzooKeyMovingTowardPrefixSearch.search(
            composingText = composing,
            inputProcessRange = null,
            surfaceProcessRange = surfaceRange,
            needTypoCorrection = false,
            useMemory = false,
            registry = registry,
            charIdMap = charIdMap,
        )
        assertTrue("readings=${result.readingsToInfo.keys}", result.readingsToInfo.isNotEmpty())
        val allIndices = result.identifierIndices.flatMap { it.second }
        assertTrue("indices=$allIndices ids=${result.identifierIndices.map { it.first }}", allIndices.isNotEmpty())
        val entries = result.identifierIndices.flatMap { (id, indices) ->
            registry.entriesForIdentifier(id, indices)
        }
        println("entries sample: ${entries.take(20).map { it.surface to it.reading }}")
        assertTrue(
            "entries count=${entries.size}",
            entries.any { it.reading.startsWith("ツカ") || it.surface == "使" },
        )
    }

    @Test
    fun lookupDicdataBuildsLatticeNodes() = runTest {
        assumeTrue(File("app/src/main/assets/azookey/cb/1285.binary").isFile || File("src/main/assets/azookey/cb/1285.binary").isFile)
        val facade = AzooKeyDicdataFacade(
            loudsLookups = listOf(AzooKeyParityGoldenFixtures.registry()),
            searchMemory = { _, _ -> emptyList() },
            connectionStore = AzooKeyParityGoldenFixtures.connectionStore(),
        )
        val composing = ComposingText.fromConvertTarget("つかっている")
        val nodes = facade.lookupDicdata(
            composingText = composing,
            inputRange = null,
            surfaceRange = AzooKeyDicdataFacade.SurfaceRange(0, null),
            needTypoCorrection = false,
            useMemory = false,
        )
        println("nodes count=${nodes.size} sample=${nodes.take(10).map { "${it.entry.surface}/${it.entry.reading} ${it.range}" }}")
        assertTrue("nodes=${nodes.size}", nodes.isNotEmpty())
        assertTrue(
            nodes.any { it.entry.reading.startsWith("ツカ") || it.entry.surface == "使" },
        )
    }
}
