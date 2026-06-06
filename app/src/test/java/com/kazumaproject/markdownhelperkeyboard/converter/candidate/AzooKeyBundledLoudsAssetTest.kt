package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyBundledLoudsAssetTest {
    @Test
    fun bundledLoudsAssetsExposeGeneratedIdentifierManifest() {
        val loudsDirectory = bundledLoudsDirectory()
        val loader = loaderFor(loudsDirectory)

        val identifiers = loader.loadIdentifierManifest()

        assertEquals(160, identifiers.size)
        assertTrue("シ should be in bundled identifiers", "シ" in identifiers)
        assertTrue("ア should be in bundled identifiers", "ア" in identifiers)
    }

    @Test
    fun bundledLoudsAssetsCanBeUsedThroughCandidateService() = runTest {
        val loudsDirectory = bundledLoudsDirectory()
        val loader = loaderFor(loudsDirectory)
        val registry = loader.loadLoudsDictionaryRegistry()
            ?: error("Bundled AzooKey LOUDS registry should load")
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

        val mainSurfaces = result.conversionResult.mainResults.map { it.string }.toSet()
        assertTrue("司会 should be found in bundled candidates $mainSurfaces", "司会" in mainSurfaces)
        assertTrue("視界 should be found in bundled candidates $mainSurfaces", "視界" in mainSurfaces)
        assertTrue("死界 should be found in bundled candidates $mainSurfaces", "死界" in mainSurfaces)
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
