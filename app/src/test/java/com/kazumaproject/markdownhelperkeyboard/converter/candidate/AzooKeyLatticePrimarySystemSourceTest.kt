package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyLatticePrimarySystemSourceTest {
    @Test
    fun latticePrimaryIncludesShikaiInTopResults() = runTest {
        val registry = loaderFor(bundledLoudsDirectory())
            .loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")

        val engine = EngineSystemDictionarySourceProvider(
            convertNormal = { SystemCandidateSourceResult(emptyList()) },
            convertOriginal = { SystemCandidateSourceResult(emptyList()) },
            convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
        )
        val system = LatticePrimarySystemDictionarySourceProvider(
            loudsLookups = listOf(registry),
            searchMemory = { _, _ -> emptyList() },
            engine = engine,
            nBest = 40,
        )

        val service = AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = 1,
                userDictionaryPrefixMatchThreshold = 1,
            ),
            searchMemory = { _, _ -> emptyList() },
            searchUserDictionary = { _, _ -> emptyList() },
            loudsDictionaryLookups = emptyList(),
            searchUserTemplate = { _, _ -> emptyList() },
            romanize = { null },
            toHankakuAlphabet = { it },
        ).create(
            systemSourceProvider = system,
            includeLoudsInAuxiliary = false,
        )

        val surfaces = service.convert(
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
        ).conversionResult.mainResults.map { it.string }

        assertTrue("司会 should appear in lattice primary results $surfaces", "司会" in surfaces)
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