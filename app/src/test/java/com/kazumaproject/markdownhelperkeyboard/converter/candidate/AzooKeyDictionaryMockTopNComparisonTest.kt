package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeConverter
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeDecoder
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本家 DictionaryMock LOUDS と Kotlin lattice primary の top-N 一致を検証する。
 * fixture が無い環境ではスキップ。
 */
class AzooKeyDictionaryMockTopNComparisonTest {
    @Test
    fun latticePrimaryTopThreeMatchesDictionaryMockForShikai() = runTest {
        val loudsDirectory = File(AzooKeyDictionaryMockPaths.loudsDirectory)
        assumeTrue("AzooKey DictionaryMock fixture is not available", loudsDirectory.isDirectory)
        val loader = dictionaryMockLoader(loudsDirectory)
        val expectedTop = expectedMockTopSurfaces(loader)
        val registry = AzooKeyLoudsDictionaryRegistry(
            loader = loader,
            identifiers = setOf("シ"),
        )
        val system = LatticePrimarySystemDictionarySourceProvider(
            loudsLookups = listOf(registry),
            searchMemory = { _, _ -> emptyList() },
            engine = emptyEngine(),
            nBest = 40,
            latticeConverter = AzooKeyLatticeConverter(
                decoder = AzooKeyLatticeDecoder(),
            ),
        )
        val service = factory(registry).create(
            systemSourceProvider = system,
            includeLoudsInAuxiliary = false,
        )
        val surfaces = service.convert(baseRequest("しかい"))
            .conversionResult.mainResults.map { it.string }

        assertTopSurfacesMatchMock(expectedTop, surfaces)
    }

    @Test
    fun loudsOnlyTopThreeMatchesDictionaryMockForShikai() = runTest {
        val loudsDirectory = File(AzooKeyDictionaryMockPaths.loudsDirectory)
        assumeTrue("AzooKey DictionaryMock fixture is not available", loudsDirectory.isDirectory)
        val loader = dictionaryMockLoader(loudsDirectory)
        val registry = AzooKeyLoudsDictionaryRegistry(
            loader = loader,
            identifiers = setOf("シ"),
        )
        val expectedTop = expectedMockTopSurfaces(loader)
        val louds = AzooKeyLoudsDictionaryCandidateSourceProvider(
            lookup = registry,
            limit = 40,
        )
        val surfaces = louds.provide(baseRequest("しかい"))
            .system
            .sortedByDescending { it.value }
            .map { it.string }

        assertTopSurfacesMatchMock(expectedTop, surfaces)
    }

    private fun expectedMockTopSurfaces(loader: AzooKeyDictionaryShardLoader): List<String> {
        val lookup = loader.loadLoudsDictionaryLookup(identifier = "シ")
            ?: error("DictionaryMock LOUDS lookup should load")
        return lookup.exactEntries("シカイ")
            .sortedByDescending { it.value }
            .map { it.surface }
            .filterNot { it == "しかい" || it == "シカイ" }
            .take(3)
    }

    private fun assertTopSurfacesMatchMock(expectedTop: List<String>, actual: List<String>) {
        assertEquals(expectedTop.first(), actual.first())
        assertTrue(
            "Expected mock top surfaces $expectedTop in $actual",
            expectedTop.all { it in actual },
        )
        val ranked = actual.filter { it in expectedTop }
        assertEquals(expectedTop, ranked.take(expectedTop.size))
    }

    private fun factory(registry: AzooKeyLoudsDictionarySearcher): AzooKeyStyleCandidateServiceFactory {
        return AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = 1,
                userDictionaryPrefixMatchThreshold = 1,
                loudsDictionaryLimit = 40,
            ),
            searchMemory = { _, _ -> emptyList() },
            searchUserDictionary = { _, _ -> emptyList() },
            loudsDictionaryLookups = listOf(registry),
            searchUserTemplate = { _, _ -> emptyList() },
            romanize = { null },
            toHankakuAlphabet = { it },
        )
    }

    private fun dictionaryMockLoader(loudsDirectory: File): AzooKeyDictionaryShardLoader {
        return AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = loudsDirectory.absolutePath,
        )
    }

    private fun emptyEngine(): EngineSystemDictionarySourceProvider {
        return EngineSystemDictionarySourceProvider(
            convertNormal = { SystemCandidateSourceResult(emptyList()) },
            convertOriginal = { SystemCandidateSourceResult(emptyList()) },
            convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
        )
    }

    private fun baseRequest(input: String): CandidateRequest {
        return CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = 40,
            useUserDictionary = false,
            useUserTemplate = false,
            useRomajiCandidates = false,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.Nothing,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Disabled,
            specialCandidateProviders = emptyList(),
        )
    }
}

object AzooKeyDictionaryMockPaths {
    const val loudsDirectory: String =
        "/private/tmp/AzooKeyKanaKanjiConverter/Tests/KanaKanjiConverterModuleTests/DictionaryMock/louds"
}