package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同梱 emoji Dicdata の value 降順と TextReplacer より Dicdata 優先を固定する。
 */
class AzooKeyBundledEmojiDictionaryOrderingTest {
    @Test
    fun bundledEgaPrefixOrdersByValueAndPrefersDicdataOverTextReplacer() {
        val search = bundledEmojiSearch()
        val results = search.searchInputPrefix("えが", limit = 10)

        assertTrue(results.isNotEmpty())
        val dicdataResults = results.filter { AzooKeyDictionaryMetadata.EmojiDicdata in it.metadata }
        assertTrue("Bundled asset should include Dicdata emoji for えが", dicdataResults.isNotEmpty())
        val dicdataValues = dicdataResults.map { it.value }
        assertTrue(
            "Dicdata emoji values should be non-increasing: $dicdataValues",
            dicdataValues.zipWithNext().all { (left, right) -> left >= right },
        )
        val firstDicdataIndex = results.indexOfFirst { AzooKeyDictionaryMetadata.EmojiDicdata in it.metadata }
        val firstTextReplacerIndex = results.indexOfFirst {
            AzooKeyDictionaryMetadata.EmojiTextReplacer in it.metadata
        }
        if (firstTextReplacerIndex >= 0) {
            assertTrue(
                "Dicdata lane should appear before TextReplacer fallback",
                firstDicdataIndex >= 0 && firstDicdataIndex < firstTextReplacerIndex,
            )
        }
        assertTrue("😀️" in results.map { it.surface }.toSet())
    }

    @Test
    fun bundledEgaPrefixSurfacesAppearInLoudsOnlyPipeline() = runTest {
        val registry = AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = AzooKeyTestAssetPaths.loudsDirectory().absolutePath,
        ).loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")

        val emojiSearch = bundledEmojiSearch()
        val service = AzooKeyStyleCandidateServiceFactory(
            auxiliaryConfig = AuxiliaryCandidateSourceConfig(
                learnedPrefixMatchThreshold = 1,
                userDictionaryPrefixMatchThreshold = 1,
                loudsDictionaryLimit = 40,
                emojiDictionaryLimit = 10,
            ),
            searchMemory = { _, _ -> emptyList() },
            searchUserDictionary = { _, _ -> emptyList() },
            loudsDictionaryLookups = listOf(registry),
            searchEmojiDictionary = { input, limit -> emojiSearch.searchInputPrefix(input, limit) },
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

        val surfaces = service.convert(
            CandidateRequest(
                input = "えが",
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
            ),
        ).conversionResult.mainResults.map { it.string }

        assertTrue("😀️ should surface via special provider in pipeline", "😀️" in surfaces)
        val prefixResults = emojiSearch.searchInputPrefix("えが", limit = 10)
        val dicdataSurface = prefixResults.firstOrNull {
            AzooKeyDictionaryMetadata.EmojiDicdata in it.metadata
        }?.surface
        val textReplacerSurface = prefixResults.firstOrNull {
            AzooKeyDictionaryMetadata.EmojiTextReplacer in it.metadata &&
                AzooKeyDictionaryMetadata.EmojiDicdata !in it.metadata
        }?.surface
        if (dicdataSurface != null && textReplacerSurface != null &&
            dicdataSurface in surfaces && textReplacerSurface in surfaces
        ) {
            assertTrue(
                "Dicdata emoji should rank before TextReplacer in merged pipeline: $surfaces",
                surfaces.indexOf(dicdataSurface) < surfaces.indexOf(textReplacerSurface),
            )
        }
    }

    private fun bundledEmojiSearch(): AzooKeyEmojiDictionarySearch {
        val emojiDir = AzooKeyTestAssetPaths.emojiDirectory()
        return AzooKeyEmojiDictionarySearch.fromAzooKeyEmojiDictionaryTexts(
            textReplacerText = File(emojiDir, "emoji_all_E17.0.txt").readText(Charsets.UTF_8),
            dicdataText = File(emojiDir, "emoji_dict_E17.0.txt").readText(Charsets.UTF_8),
        )
    }
}