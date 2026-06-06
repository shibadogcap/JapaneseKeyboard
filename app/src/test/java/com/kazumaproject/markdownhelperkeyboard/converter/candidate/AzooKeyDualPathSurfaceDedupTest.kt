package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DualPath（engine system + LOUDS auxiliary）で同一 surface が重複しないこと。
 * 移行期間の [AzooKeyStyleCandidateRanker] による surface dedup の回帰。
 */
class AzooKeyDualPathSurfaceDedupTest {
    @Test
    fun dualPathDedupesEngineAndLoudsDuplicateSurfaces() = runTest {
        val registry = loaderFor(bundledLoudsDirectory())
            .loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")

        val engine = SystemKanaKanjiCandidateSourceProvider(
            convertNormal = {
                SystemCandidateSourceResult(
                    candidates = listOf(
                        Candidate(
                            string = "司会",
                            type = CandidateType.NBEST,
                            length = 3.toUByte(),
                            score = -5000,
                            value = -50f,
                            yomi = "しかい",
                        ),
                        Candidate(
                            string = "視界",
                            type = CandidateType.NBEST,
                            length = 3.toUByte(),
                            score = -6000,
                            value = -60f,
                            yomi = "しかい",
                        ),
                    ),
                )
            },
            convertOriginal = { SystemCandidateSourceResult(emptyList()) },
            convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
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
            systemSourceProvider = engine,
            includeLoudsInAuxiliary = true,
            includeMemoryInAuxiliary = true,
        )

        val request = CandidateRequest(
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
        val mainResults = service.convert(request).conversionResult.mainResults
        val surfaces = mainResults.map { it.string }

        assertEquals(surfaces.size, surfaces.distinct().size)
        assertTrue("LOUDS should still surface 司会", "司会" in surfaces)
        val shikai = mainResults.first { it.string == "司会" }
        assertTrue(
            "Higher PValue from LOUDS should win over low-score engine stub",
            shikai.value > -50f,
        )
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