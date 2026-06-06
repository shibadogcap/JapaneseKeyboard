package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LOUDS primary 経路: system は LOUDS のみ、engine は空でも golden と一致する。
 */
class AzooKeyLoudsPrimarySystemSourceTest {
    @Test
    fun loudsPrimaryMatchesGoldenTopFive() = runTest {
        val registry = loaderFor(bundledLoudsDirectory())
            .loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")

        val louds = AzooKeyLoudsDictionaryCandidateSourceProvider(
            lookup = registry,
            limit = 40,
        )
        val engine = EngineSystemDictionarySourceProvider(
            convertNormal = { SystemCandidateSourceResult(emptyList()) },
            convertOriginal = { SystemCandidateSourceResult(emptyList()) },
            convertWithoutPrediction = { SystemCandidateSourceResult(emptyList()) },
        )
        val system = LoudsPrimarySystemDictionarySourceProvider(
            louds = louds,
            engine = engine,
        )

        val service = AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = 1,
                userDictionaryPrefixMatchThreshold = 1,
            ),
            searchMemory = { _, _ -> emptyList() },
            searchUserDictionary = { _, _ -> emptyList() },
            loudsDictionaryLookups = listOf(registry),
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

        assertEquals(
            listOf("司会", "視界", "歯科医", "市会", "士会"),
            surfaces.take(5),
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