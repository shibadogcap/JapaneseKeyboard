package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Documents current behavior when LOUDS auxiliary source is the only system dictionary contributor.
 * When [KanaKanjiEngine] also returns the same surface, mixer/post-process dedup policy must be
 * validated separately (see Phase 4 in docs/azookey-candidate-service-boundary.md).
 */
class AzooKeyLoudsEngineDuplicateSurfaceTest {
    @Test
    fun loudsOnlyPathDoesNotDuplicateSurfacesInMainResults() = runTest {
        val registry = loaderFor(bundledLoudsDirectory())
            .loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")

        val service = AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = 1,
                userDictionaryPrefixMatchThreshold = 1,
                loudsDictionaryLimit = 20,
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

        val surfaces = service.convert(
            CandidateRequest(
                input = "しかい",
                mode = CandidateRequestMode.Normal,
                nBest = 20,
                useUserDictionary = false,
                useUserTemplate = false,
                useRomajiCandidates = false,
                useBunsetsu = false,
                useOmissionSearch = false,
                japanesePredictionMode = AzooKeyStylePredictionMode.Disabled,
                englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
                learningType = AzooKeyStyleLearningType.Nothing,
                typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
                specialCandidateProviders = emptyList(),
            )
        ).conversionResult.mainResults.map { it.string }

        assertEquals(surfaces.size, surfaces.distinct().size)
    }

    private fun loaderFor(loudsDirectory: File): AzooKeyDictionaryShardLoader {
        return AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = loudsDirectory.absolutePath,
        )
    }

    private fun bundledLoudsDirectory(): File {
        return listOf(
            File("app/src/main/assets/louds"),
            File("src/main/assets/louds"),
        ).firstOrNull { it.isDirectory }
            ?: error("Bundled LOUDS assets are missing")
    }
}