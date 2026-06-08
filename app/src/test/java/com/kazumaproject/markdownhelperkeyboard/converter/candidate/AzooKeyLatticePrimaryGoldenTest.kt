package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Lattice primary + 同梱 cb/mm がある環境では LOUDS primary と同じ top-5 を期待する。
 */
class AzooKeyLatticePrimaryGoldenTest {
    @Test
    fun latticePrimaryMatchesLoudsGoldenTopFiveWhenConnectionAssetsPresent() = runTest {
        assumeTrue(
            "Requires azookey/cb assets",
            File("app/src/main/assets/azookey/cb/1285.binary").isFile ||
                File("src/main/assets/azookey/cb/1285.binary").isFile,
        )

        val registry = loaderFor(bundledLoudsDirectory())
            .loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")

        val azookeyDir = listOf(
            File("app/src/main/assets/azookey"),
            File("src/main/assets/azookey"),
        ).first { File(it, "cb/1285.binary").isFile }
        val connectionStore = checkNotNull(
            com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
                .fromDirectory(azookeyDir),
        ) { "Connection cost store should load" }

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
            latticeConverter = com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeConverter(
                decoder = com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyLatticeDecoder(
                    connectionCost = { former, latter ->
                        connectionStore.getConnectionCost(former, latter)
                    },
                ),
            ),
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


        val requestObj = CandidateRequest(
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
        val mainCandidates = service.convert(requestObj).conversionResult.mainResults
        println("MAIN CANDIDATES:")
        mainCandidates.forEach { println("candidate: surface=${it.string} yomi=${it.yomi} score=${it.score} value=${it.value} type=${it.type}") }
        val surfaces = mainCandidates.map { it.string }
        assertEquals(
            listOf("司会", "視界", "しかい", "歯科医", "市会"),
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