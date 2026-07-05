package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import com.kazumaproject.markdownhelperkeyboard.converter.api.appendRoman2KanaCharAtEnd
import com.kazumaproject.markdownhelperkeyboard.converter.api.insertDirectAtCursor
import com.kazumaproject.markdownhelperkeyboard.converter.api.insertRoman2KanaAtCursor
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyConverterEngineResult
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyKanaKanjiConverterEngine
import com.kazumaproject.markdownhelperkeyboard.converter.lattice.AzooKeyConnectionCostStore
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.DefaultRomajiToKanaMap
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

    /** Swift [ConverterTests.requestOptions] と同じ候補オプション。 */
    fun swiftAlignedConvertOptions(request: CandidateRequest): ConvertRequestOptions {
        return toConvertOptions(request).copy(
            fullWidthRomanCandidate = false,
            halfWidthKanaCandidate = false,
            englishCandidateInRoman2KanaInput = true,
        )
    }

    fun defaultRoman2KanaTransducer(): AzooKeyRoman2KanaTransducer =
        AzooKeyRoman2KanaTransducer.default()

    fun buildSequentialDirectComposingText(query: String): ComposingText {
        var text = ComposingText.fromConvertTarget("")
        for (ch in query) {
            text = text.insertDirectAtCursor(ch.toString())
        }
        return text
    }

    fun buildSequentialRoman2KanaComposingText(
        query: String,
        transducer: AzooKeyRoman2KanaTransducer = defaultRoman2KanaTransducer(),
    ): ComposingText {
        var text = ComposingText.fromConvertTarget("")
        for (ch in query) {
            text = text.appendRoman2KanaCharAtEnd(ch, transducer)
        }
        return text
    }

    suspend fun convert(
        engine: AzooKeyKanaKanjiConverterEngine,
        request: CandidateRequest,
        session: ConversionSession = ConversionSession(),
        swiftAlignedOptions: Boolean = false,
        roman2KanaTransducer: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): AzooKeyStyleConversionResult {
        return convertWithEngine(
            engine,
            request,
            session,
            swiftAlignedOptions,
            roman2KanaTransducer,
        ).conversionResult
    }

    suspend fun convertWithEngine(
        engine: AzooKeyKanaKanjiConverterEngine,
        request: CandidateRequest,
        session: ConversionSession = ConversionSession(),
        swiftAlignedOptions: Boolean = false,
        roman2KanaTransducer: AzooKeyRoman2KanaTransducer = AzooKeyRoman2KanaTransducer.Identity,
    ): AzooKeyConverterEngineResult {
        request.completedCandidate?.let { engine.setCompletedData(session.sessionId, it) }
        val options = if (swiftAlignedOptions) {
            swiftAlignedConvertOptions(request).copy(
                roman2KanaTransducer = roman2KanaTransducer,
            )
        } else {
            toConvertOptions(request).copy(
                roman2KanaTransducer = roman2KanaTransducer,
            )
        }
        return engine.requestCandidates(
            inputData = request.composingText ?: ComposingText.fromConvertTarget(request.input),
            options = options,
            session = session,
            searchMemory = { _, _ -> emptyList() },
            searchUserTemplate = { _, _ -> emptyList() },
        )
    }

    fun romanIttaiComposingText(): ComposingText {
        val transducer = com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer.default()
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
