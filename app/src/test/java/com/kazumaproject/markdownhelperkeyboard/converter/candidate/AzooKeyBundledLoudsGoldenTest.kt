package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for bundled AzooKey LOUDS assets: representative reading and stable ordering.
 */
class AzooKeyBundledLoudsGoldenTest {
    /** Bump when bundled `app/src/main/assets/louds` content changes. */
    private val bundledLoudsGoldenAssetVersion = "bundled-louds-2026-06-05"
    @Test
    fun shikaiReadingReturnsExpectedSurfacesWithoutDuplicatesInTopResults() = runTest {
        val registry = loaderFor(AzooKeyTestAssetPaths.loudsDirectory())
            .loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")

        val service = loudsOnlyService(registry)
        val result = service.convert(
            CandidateRequest(
                input = "しかい",
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
                typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
                specialCandidateProviders = emptyList(),
            )
        )

        val topSurfaces = result.conversionResult.mainResults
            .take(10)
            .map { it.string }

        assertEquals(topSurfaces.size, topSurfaces.toSet().size)

        assertEquals(
            listOf("司会", "視界", "歯科医", "市会", "士会"),
            topSurfaces.take(5),
        )
    }

    @Test
    fun nihonReadingReturnsExpectedTopFiveSurfacesWithoutDuplicates() = runTest {
        val registry = loaderFor(AzooKeyTestAssetPaths.loudsDirectory())
            .loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")

        val service = loudsOnlyService(registry)
        val topSurfaces = service.convert(
            CandidateRequest(
                input = "にほん",
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
                typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
                specialCandidateProviders = emptyList(),
            ),
        ).conversionResult.mainResults.take(10).map { it.string }

        assertEquals(topSurfaces.size, topSurfaces.toSet().size)
        assertEquals(listOf("日本", "2本", "ニホン"), topSurfaces.take(3))
        assertTrue(
            "tail ranks may shift with asset updates ($bundledLoudsGoldenAssetVersion): $topSurfaces",
            topSurfaces.drop(3).take(2).all { it in setOf("二本", "にほん") },
        )
    }

    private fun loudsOnlyService(registry: AzooKeyLoudsDictionaryRegistry): AzooKeyStyleCandidateService {
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
        ).create(
            systemSourceProvider = SystemKanaKanjiCandidateSourceProvider(
                convertNormal = { SystemCandidateSourceResult(emptyList()) },
                convertOriginal = { SystemCandidateSourceResult(emptyList()) },
                convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
            ),
        )
    }

    private fun loaderFor(loudsDirectory: File): AzooKeyDictionaryShardLoader {
        return AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = loudsDirectory.absolutePath,
        )
    }
}
