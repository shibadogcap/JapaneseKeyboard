package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyParityGoldenFixtures
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleZenzaiMode
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyKanaKanjiConverterEngine
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.AzooKeyZenzaiTypoCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPromptContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalTypoCorrectionApiTest {
    private class RecordingTypoEngine : ZenzEnginePort {
        var typoEncodeCalls: Int = 0
            private set

        override suspend fun generateWithContext(
            prompt: ZenzPromptContext,
            inputKatakana: String,
            maxTokens: Int,
        ): String = ""

        override suspend fun predictNextInputText(
            prompt: ZenzPromptContext,
            composingText: String,
            count: Int,
            minLength: Int,
            maxEntropy: Float?,
            possibleNexts: List<String>,
        ): String = ""

        override suspend fun typoEncodeRaw(text: String): IntArray {
            typoEncodeCalls += 1
            return intArrayOf(1, 2, 3)
        }

        override suspend fun typoNextLogProbs(
            promptPrefix: String,
            emittedTokenIds: IntArray,
        ): FloatArray? = null

        override fun typoTokenToSingleCharacter(tokenId: Int): Char? = null

        override fun vocabSize(): Int = 0

        override suspend fun candidateEvaluate(
            prompt: ZenzPromptContext,
            inputKatakana: String,
            candidate: String,
            requestRichCandidates: Boolean,
        ): String = ""

        override suspend fun scoreCandidates(
            prompt: ZenzPromptContext,
            inputKatakana: String,
            candidates: List<String>,
        ): FloatArray = FloatArray(candidates.size)
    }

    @Test
    fun engineReturnsEmptyWhenZenzaiDisabled() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyKanaKanjiConverterEngine(
            registry = AzooKeyParityGoldenFixtures.registry(),
            connectionStore = AzooKeyParityGoldenFixtures.connectionStore(),
            zenzEngine = RecordingTypoEngine(),
        )
        val result = engine.experimentalRequestTypoCorrection(
            leftSideContext = "やあ、",
            composingText = ComposingText.fromConvertTarget("ojsyougozainasu"),
            options = ConvertRequestOptions(zenzaiMode = AzooKeyStyleZenzaiMode.Off),
            inputStyle = InputStyle.Roman2Kana,
            session = ConversionSession(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun engineInvokesTypoGeneratorWhenZenzaiEnabled() = AzooKeyParityGoldenFixtures.runWithAssets {
        val typoEngine = RecordingTypoEngine()
        val engine = AzooKeyKanaKanjiConverterEngine(
            registry = AzooKeyParityGoldenFixtures.registry(),
            connectionStore = AzooKeyParityGoldenFixtures.connectionStore(),
            zenzEngine = typoEngine,
        )
        engine.experimentalRequestTypoCorrection(
            leftSideContext = "やあ、",
            composingText = ComposingText.fromConvertTarget("ojsyougozainasu"),
            options = ConvertRequestOptions(zenzaiMode = AzooKeyStyleZenzaiMode.On),
            inputStyle = InputStyle.Roman2Kana,
            session = ConversionSession(),
        )
        assertTrue(typoEngine.typoEncodeCalls > 0)
    }

    @Test
    fun kanaKanjiConverterInterfaceExposesTypoCorrection() = runTest {
        val expected = listOf(
            AzooKeyZenzaiTypoCandidate(
                correctedInput = "ohayougozaimasu",
                convertedText = "おはようございます",
                score = 1f,
                lmScore = 0f,
                channelCost = 0f,
                prominence = 1f,
            ),
        )
        val converter = KanaKanjiConverterStub { _, _, _, _, _ ->
            ConvertCandidatesResponse(ConversionResult(emptyList()), null)
        }
        val stub = object : KanaKanjiConverter by converter {
            override suspend fun experimentalRequestTypoCorrection(
                leftSideContext: String,
                composingText: ComposingText,
                options: ConvertRequestOptions,
                inputStyle: InputStyle,
                session: ConversionSession,
            ): List<AzooKeyZenzaiTypoCandidate> = expected
        }
        val result = stub.experimentalRequestTypoCorrection(
            leftSideContext = "やあ、",
            composingText = ComposingText.fromConvertTarget("ojsyougozainasu"),
            options = ConvertRequestOptions(zenzaiMode = AzooKeyStyleZenzaiMode.On),
            inputStyle = InputStyle.Roman2Kana,
            session = ConversionSession(),
        )
        assertEquals(expected, result)
    }
}
