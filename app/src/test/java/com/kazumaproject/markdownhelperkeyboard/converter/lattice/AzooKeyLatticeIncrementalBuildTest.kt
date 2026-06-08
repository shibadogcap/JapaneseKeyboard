package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntryMapper
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyLoudsDictionarySearcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyLatticeIncrementalBuildTest {
    @Test
    fun incrementalBuildReusesNodesInsidePreviousSpan() = runTest {
        var lookupCalls = 0
        val lookup = CountingLookup { lookupCalls += it }
        val store = AzooKeyLoudsBackedDicdataStore(
            loudsLookups = listOf(lookup),
            searchMemory = { _, _ -> emptyList() },
        )

        val first = store.buildLatticeNodes(input = "しか", useMemory = false)
        lookupCalls = 0
        val second = store.buildLatticeNodesIncremental(
            input = "しかい",
            useMemory = false,
            previousNormalizedInput = "シカ",
            previousNodes = first,
        )

        assertTrue(second.any { it.entry.surface == "司会" && it.endIndex == 3 })
        assertTrue(second.any { it.endIndex <= 2 })
        assertTrue(lookupCalls > 0)
    }

    @Test
    fun incrementalBuildFallsBackWhenInputIsNotPrefixExtension() = runTest {
        val lookup = CountingLookup { }
        val store = AzooKeyLoudsBackedDicdataStore(
            loudsLookups = listOf(lookup),
            searchMemory = { _, _ -> emptyList() },
        )
        val previous = store.buildLatticeNodes(input = "しかい", useMemory = false)
        val rebuilt = store.buildLatticeNodesIncremental(
            input = "かい",
            useMemory = false,
            previousNormalizedInput = "シカイ",
            previousNodes = previous,
        )

        assertEquals(
            store.buildLatticeNodes(input = "かい", useMemory = false).map { nodeKey(it) }.toSet(),
            rebuilt.map { nodeKey(it) }.toSet(),
        )
    }

    @Test
    fun converterUpdatesIncrementalStateOnPrefixGrowth() = runTest {
        val lookup = CountingLookup { }
        val state = AzooKeyLatticeIncrementalState()
        val converter = AzooKeyLatticeConverter()

        converter.convert(
            request = request("しか"),
            loudsLookups = listOf(lookup),
            searchMemory = { _, _ -> emptyList() },
            nBest = 5,
            incrementalState = state,
        )
        assertEquals("シカ", state.normalizedInput)
        assertTrue(state.latticeNodes.isNotEmpty())

        converter.convert(
            request = request("しかい"),
            loudsLookups = listOf(lookup),
            searchMemory = { _, _ -> emptyList() },
            nBest = 5,
            incrementalState = state,
        )
        assertEquals("シカイ", state.normalizedInput)
        assertTrue(state.latticeNodes.any { it.entry.surface == "司会" })
    }

    @Test
    fun converterFiltersNodesOnDeletion() = runTest {
        var lookupCalls = 0
        val lookup = CountingLookup { lookupCalls += 1 }
        val state = AzooKeyLatticeIncrementalState()
        val converter = AzooKeyLatticeConverter()

        converter.convert(
            request = request("しかい"),
            loudsLookups = listOf(lookup),
            searchMemory = { _, _ -> emptyList() },
            nBest = 5,
            incrementalState = state,
        )
        assertEquals("シカイ", state.normalizedInput)
        assertTrue(state.latticeNodes.isNotEmpty())
        lookupCalls = 0

        converter.convert(
            request = request("しか"),
            loudsLookups = listOf(lookup),
            searchMemory = { _, _ -> emptyList() },
            nBest = 5,
            incrementalState = state,
        )

        assertEquals(0, lookupCalls)
        assertEquals("シカ", state.normalizedInput)
        assertTrue(state.latticeNodes.isNotEmpty())
        assertTrue(state.latticeNodes.all { it.endIndex <= 2 })
    }

    private fun nodeKey(node: AzooKeyLatticeNode): String {
        return "${node.startIndex}:${node.endIndex}:${node.entry.surface}"
    }

    private fun request(input: String): com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest {
        return com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest(
            input = input,
            mode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode.Normal,
            nBest = 5,
            useUserDictionary = false,
            useUserTemplate = false,
            useRomajiCandidates = false,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode.Disabled,
            englishPredictionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode.Disabled,
            learningType = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType.Nothing,
            typoCorrectionMode = com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode.Disabled,
        )
    }

    private class CountingLookup(
        private val onPrefix: (Int) -> Unit,
    ) : AzooKeyLoudsDictionarySearcher {
        override fun exactEntries(reading: String): List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry> {
            onPrefix(reading.length)
            return when (reading) {
                "シカ", "しか" -> listOf(entry("し", "し", 0, 1))
                "シカイ", "しかい" -> listOf(entry("司会", "しかい", 0, 3))
                else -> emptyList()
            }
        }

        override fun prefixEntries(
            reading: String,
            maxDepth: Int,
            maxCount: Int,
        ): List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry> {
            onPrefix(reading.length)
            return exactEntries(reading)
        }

        override fun commonPrefixEntries(reading: String): List<com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry> {
            onPrefix(reading.length)
            val list = mutableListOf<com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry>()
            if (reading.startsWith("シ") || reading.startsWith("し")) {
                list.add(entry("し", "し", 0, 1))
            }
            if (reading.startsWith("シカイ") || reading.startsWith("しかい")) {
                list.add(entry("司会", "しかい", 0, 3))
            }
            return list
        }

        private fun entry(
            surface: String,
            reading: String,
            start: Int,
            end: Int,
        ): com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry {
            return AzooKeyDictionaryEntryMapper.memory(
                surface = surface,
                reading = reading,
                leftId = 1285,
                rightId = 1285,
                legacyScore = -5,
                readingLength = end - start,
            )
        }
    }
}