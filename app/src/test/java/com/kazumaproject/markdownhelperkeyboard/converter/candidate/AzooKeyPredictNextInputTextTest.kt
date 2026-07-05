package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.api.ComposingText
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConvertRequestOptions
import com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.api.InputStyle
import com.kazumaproject.markdownhelperkeyboard.converter.core.AzooKeyKanaKanjiConverterEngine
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzEnginePort
import com.kazumaproject.markdownhelperkeyboard.converter.zenz.ZenzPromptContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyPredictNextInputTextTest {
    private class FakeZenzEngine(
        private val predictions: List<String>,
    ) : ZenzEnginePort {
        var callCount: Int = 0
            private set

        override suspend fun generateWithContext(
            prompt: ZenzPromptContext,
            inputKatakana: String,
            maxTokens: Int,
        ): String = predictions.getOrElse(callCount) { "" }.also { callCount++ }

        override suspend fun predictNextInputText(
            prompt: ZenzPromptContext,
            composingText: String,
            count: Int,
            possibleNexts: List<String>,
        ): String = predictions.getOrElse(callCount) { "" }.also { callCount++ }

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
    fun predictNextInputTextReturnsEmptyWhenZenzDisabled() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyKanaKanjiConverterEngine(
            registry = AzooKeyParityGoldenFixtures.registry(),
            connectionStore = AzooKeyParityGoldenFixtures.connectionStore(),
            zenzEngine = FakeZenzEngine(listOf("いうえ")),
        )
        val result = engine.predictNextInputText(
            leftSideContext = "",
            composingText = ComposingText.fromConvertTarget("あ"),
            count = 3,
            options = ConvertRequestOptions(zenzaiMode = AzooKeyStyleZenzaiMode.Off),
            session = ConversionSession(),
        )
        assertEquals("", result.predictedText)
        assertEquals(0, result.suffixCount)
    }

    @Test
    fun predictNextInputTextCachesRemainingSuffix() = AzooKeyParityGoldenFixtures.runWithAssets {
        val fakeZenz = FakeZenzEngine(listOf("いうえお"))
        val engine = AzooKeyKanaKanjiConverterEngine(
            registry = AzooKeyParityGoldenFixtures.registry(),
            connectionStore = AzooKeyParityGoldenFixtures.connectionStore(),
            zenzEngine = fakeZenz,
        )
        val session = ConversionSession()
        val options = ConvertRequestOptions(
            zenzaiMode = AzooKeyStyleZenzaiMode.On,
            zenzProfile = "test-profile",
        )
        val first = engine.predictNextInputText(
            leftSideContext = "左",
            composingText = ComposingText.fromConvertTarget("あ"),
            count = 10,
            options = options,
            session = session,
            inputStyle = InputStyle.Direct,
        )
        assertEquals("いうえお", first.predictedText)
        assertEquals(1, fakeZenz.callCount)

        val second = engine.predictNextInputText(
            leftSideContext = "左",
            composingText = ComposingText.fromConvertTarget("あい"),
            count = 10,
            options = options,
            session = session,
            inputStyle = InputStyle.Direct,
        )
        assertEquals("うえお", second.predictedText)
        assertEquals(1, fakeZenz.callCount)
    }

    @Test
    fun predictNextInputTextInvalidatesCacheWhenContextChanges() = AzooKeyParityGoldenFixtures.runWithAssets {
        val fakeZenz = FakeZenzEngine(listOf("いう", "かき"))
        val engine = AzooKeyKanaKanjiConverterEngine(
            registry = AzooKeyParityGoldenFixtures.registry(),
            connectionStore = AzooKeyParityGoldenFixtures.connectionStore(),
            zenzEngine = fakeZenz,
        )
        val session = ConversionSession()
        val options = ConvertRequestOptions(zenzaiMode = AzooKeyStyleZenzaiMode.On)
        engine.predictNextInputText(
            leftSideContext = "左",
            composingText = ComposingText.fromConvertTarget("あ"),
            count = 5,
            options = options,
            session = session,
        )
        val afterContextChange = engine.predictNextInputText(
            leftSideContext = "右",
            composingText = ComposingText.fromConvertTarget("あ"),
            count = 5,
            options = options,
            session = session,
        )
        assertEquals("かき", afterContextChange.predictedText)
        assertEquals(2, fakeZenz.callCount)
    }

    @Test
    fun predictNextInputTextInvalidatesCacheWhenInputDiverges() = AzooKeyParityGoldenFixtures.runWithAssets {
        val fakeZenz = FakeZenzEngine(listOf("いう", "かき"))
        val engine = AzooKeyKanaKanjiConverterEngine(
            registry = AzooKeyParityGoldenFixtures.registry(),
            connectionStore = AzooKeyParityGoldenFixtures.connectionStore(),
            zenzEngine = fakeZenz,
        )
        val session = ConversionSession()
        val options = ConvertRequestOptions(zenzaiMode = AzooKeyStyleZenzaiMode.On)
        engine.predictNextInputText(
            leftSideContext = "",
            composingText = ComposingText.fromConvertTarget("あ"),
            count = 5,
            options = options,
            session = session,
        )
        val diverged = engine.predictNextInputText(
            leftSideContext = "",
            composingText = ComposingText.fromConvertTarget("か"),
            count = 5,
            options = options,
            session = session,
        )
        assertTrue(diverged.predictedText.isNotEmpty())
        assertEquals(2, fakeZenz.callCount)
    }
}
