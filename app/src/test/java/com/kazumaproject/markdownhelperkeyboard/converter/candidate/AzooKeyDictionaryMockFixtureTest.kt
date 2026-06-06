package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyDictionaryMockFixtureTest {
    @Test
    fun loudsLookupMatchesAzooKeyDictionaryMockForShikai() {
        val loudsDirectory = File(AzooKeyDictionaryMockPaths.loudsDirectory)
        assumeTrue("AzooKey DictionaryMock fixture is not available", loudsDirectory.isDirectory)
        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = loudsDirectory.absolutePath,
        )
        val lookup = loader.loadLoudsDictionaryLookup(identifier = "シ")
            ?: error("DictionaryMock LOUDS lookup should load")

        val exactEntries = lookup.exactEntries("シカイ")

        val surfaces = exactEntries.map { it.surface }.toSet()
        assertTrue("司会 should be found in $surfaces", "司会" in surfaces)
        assertTrue("視界 should be found in $surfaces", "視界" in surfaces)
        assertTrue("死界 should be found in $surfaces", "死界" in surfaces)
    }

    @Test
    fun loudsRegistryNormalizesHiraganaInputForAzooKeyDictionaryMock() {
        val loudsDirectory = File(AzooKeyDictionaryMockPaths.loudsDirectory)
        assumeTrue("AzooKey DictionaryMock fixture is not available", loudsDirectory.isDirectory)
        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = loudsDirectory.absolutePath,
        )
        val registry = AzooKeyLoudsDictionaryRegistry(
            loader = loader,
            identifiers = setOf("シ"),
        )

        val exactEntries = registry.exactEntries("しかい")

        val surfaces = exactEntries.map { it.surface }.toSet()
        assertTrue("司会 should be found in $surfaces", "司会" in surfaces)
        assertTrue("視界 should be found in $surfaces", "視界" in surfaces)
        assertTrue("死界 should be found in $surfaces", "死界" in surfaces)
    }

    @Test
    fun candidateServiceUsesAzooKeyDictionaryMockLoudsAsSystemCandidates() = runTest {
        val loudsDirectory = File(AzooKeyDictionaryMockPaths.loudsDirectory)
        assumeTrue("AzooKey DictionaryMock fixture is not available", loudsDirectory.isDirectory)
        val loader = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = loudsDirectory.absolutePath,
        )
        val registry = AzooKeyLoudsDictionaryRegistry(
            loader = loader,
            identifiers = setOf("シ"),
        )
        val service = AzooKeyStyleCandidateServiceFactory(
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
        ).create(
            systemSourceProvider = SystemKanaKanjiCandidateSourceProvider(
                convertNormal = { SystemCandidateSourceResult(emptyList()) },
                convertOriginal = { SystemCandidateSourceResult(emptyList()) },
                convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
            )
        )

        val result = service.convert(baseRequest(input = "しかい"))

        val sourceSurfaces = result.sources.system.map { it.string }.toSet()
        val mainSurfaces = result.conversionResult.mainResults.map { it.string }.toSet()
        assertTrue("司会 should be found in source $sourceSurfaces", "司会" in sourceSurfaces)
        assertTrue("視界 should be found in source $sourceSurfaces", "視界" in sourceSurfaces)
        assertTrue("死界 should be found in source $sourceSurfaces", "死界" in sourceSurfaces)
        assertTrue("司会 should be found in main candidates $mainSurfaces", "司会" in mainSurfaces)
        assertTrue("視界 should be found in main candidates $mainSurfaces", "視界" in mainSurfaces)
        assertTrue("死界 should be found in main candidates $mainSurfaces", "死界" in mainSurfaces)
    }

    private fun baseRequest(input: String): CandidateRequest {
        return CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = 40,
            useUserDictionary = true,
            useUserTemplate = true,
            useRomajiCandidates = true,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.OnlyOutput,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
            specialCandidateProviders = emptyList(),
        )
    }

}
