package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.insertRoman2KanaAtCursor
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyConverterEngineResult
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyKanaKanjiConverterEngine
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue

internal object AzooKeyParityGoldenFixtures {
    fun assumeConnectionAssetsPresent() {
        assumeTrue(
            "Requires azookey/cb assets",
            File("app/src/main/assets/azookey/cb/1285.binary").isFile ||
                File("src/main/assets/azookey/cb/1285.binary").isFile,
        )
    }

    fun azookeyAssetDirectory(): File {
        return listOf(
            File("app/src/main/assets/azookey"),
            File("src/main/assets/azookey"),
        ).first { File(it, "cb/1285.binary").isFile }
    }

    fun connectionStore(): AzooKeyConnectionCostStore {
        return checkNotNull(AzooKeyConnectionCostStore.fromDirectory(azookeyAssetDirectory())) {
            "Connection cost store should load"
        }
    }

    fun loader(): AzooKeyDictionaryShardLoader {
        return AzooKeyDictionaryShardLoader(
            readBytes = { path -> File(path).takeIf { it.isFile }?.readBytes() },
            loudsDirectory = AzooKeyTestAssetPaths.loudsDirectory().absolutePath,
        )
    }

    fun registry(): AzooKeyLoudsDictionaryRegistry {
        return loader().loadLoudsDictionaryRegistry()
            ?: error("Bundled LOUDS registry should load")
    }

    fun engine(nBest: Int = 10): AzooKeyKanaKanjiConverterEngine {
        return AzooKeyKanaKanjiConverterEngine(
            registry = registry(),
            connectionStore = connectionStore(),
        )
    }

    fun defaultRequest(
        input: String,
        nBest: Int = 10,
        composingText: ComposingText? = null,
        previousInput: String? = null,
        previousComposingText: ComposingText? = null,
        completedCandidate: Candidate? = null,
        requireJapanesePrediction: Boolean = false,
    ): CandidateRequest {
        val predictionMode = if (requireJapanesePrediction) {
            AzooKeyStylePredictionMode.AutoMix
        } else {
            AzooKeyStylePredictionMode.Disabled
        }
        return CandidateRequest(
            input = input,
            mode = CandidateRequestMode.Normal,
            nBest = nBest,
            useUserDictionary = false,
            useUserTemplate = false,
            useRomajiCandidates = false,
            useBunsetsu = false,
            useOmissionSearch = false,
            japanesePredictionMode = predictionMode,
            englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
            learningType = AzooKeyStyleLearningType.Nothing,
            typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Disabled,
            specialCandidateProviders = emptyList(),
            composingText = composingText ?: ComposingText.fromConvertTarget(input),
            previousInput = previousInput,
            previousComposingText = previousComposingText,
            completedCandidate = completedCandidate,
        )
    }

    fun toConvertOptions(request: CandidateRequest): ConvertRequestOptions {
        return ConvertRequestOptions(
            nBest = request.nBest,
            requireJapanesePrediction = request.japanesePredictionMode,
            requireEnglishPrediction = request.englishPredictionMode,
            learningType = request.learningType,
            zenzaiMode = request.zenzaiMode,
            experimentalZenzaiPredictiveInput = request.experimentalZenzaiPredictiveInput,
            typoCorrectionMode = request.typoCorrectionMode,
            fullWidthRomanCandidate = true,
            halfWidthKanaCandidate = true,
            specialCandidateProviders = request.specialCandidateProviders,
            useUserDictionary = request.useUserDictionary,
            useUserTemplate = request.useUserTemplate,
            useRomajiCandidates = request.useRomajiCandidates,
            useBunsetsu = request.useBunsetsu,
            useOmissionSearch = request.useOmissionSearch,
        )
    }

    suspend fun convert(
        engine: AzooKeyKanaKanjiConverterEngine,
        request: CandidateRequest,
        session: ConversionSession = ConversionSession(),
    ): AzooKeyStyleConversionResult {
        return convertWithEngine(engine, request, session).conversionResult
    }

    suspend fun convertWithEngine(
        engine: AzooKeyKanaKanjiConverterEngine,
        request: CandidateRequest,
        session: ConversionSession = ConversionSession(),
    ): AzooKeyConverterEngineResult {
        request.completedCandidate?.let { engine.setCompletedData(session.sessionId, it) }
        return engine.requestCandidates(
            inputData = request.composingText ?: ComposingText.fromConvertTarget(request.input),
            options = toConvertOptions(request),
            session = session,
            searchMemory = { _, _ -> emptyList() },
            searchUserTemplate = { _, _ -> emptyList() },
        )
    }

    fun romanIttaiComposingText(): ComposingText {
        val transducer = com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.fromMap(
            mapOf(
                "i" to ("い" to 1),
                "t" to ("t" to 1),
                "ta" to ("た" to 2),
                "it" to ("いt" to 2),
                "itt" to ("いt" to 3),
                "itta" to ("いた" to 4),
                "ittai" to ("いったい" to 5),
            ),
        )
        return ComposingText.fromConvertTarget("")
            .insertRoman2KanaAtCursor("ittai", transducer)
    }

    internal fun assertTopThreeContainsExactReading(candidates: List<Candidate>, input: String) {
        val target = input.hiraganaToKatakana()
        val hasExact = candidates.take(3).any { candidate ->
            candidate.yomi?.hiraganaToKatakana() == target || candidate.string == input
        }
        org.junit.Assert.assertTrue("Top-3 should contain exact reading match for $input", hasExact)
    }

    fun runWithAssets(block: suspend () -> Unit) = runTest {
        assumeConnectionAssetsPresent()
        block()
    }
}
