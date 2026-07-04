package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.InputStreamReader

/**
 * Kotlin golden と Swift CLI 出力（macOS）の diff。Swift 出力が無い場合は Kotlin golden のみ検証。
 */
class AzooKeySwiftKotlinParityTest {
    private val gson = Gson()

    @Test
    fun kotlinGoldenMatchesFixtures() = AzooKeyParityGoldenFixtures.runWithAssets {
        val engine = AzooKeyParityGoldenFixtures.engine()
        val fixtures = loadFixtures()
        for (fixture in fixtures) {
            engine.stopComposition()
            if (fixture.gradualInput != null) {
                val gradual = fixture.gradualInput
                var text = ""
                for (ch in gradual) {
                    text += ch
                    val result = AzooKeyParityGoldenFixtures.convert(
                        engine,
                        AzooKeyParityGoldenFixtures.defaultRequest(
                            input = text,
                            requireJapanesePrediction = fixture.requireJapanesePrediction,
                        ),
                    )
                    if (text.length == gradual.length) {
                        fixture.top1?.let { assertTop1(result.mainResults, it) }
                    }
                }
            } else {
                val result = AzooKeyParityGoldenFixtures.convert(
                    engine,
                    AzooKeyParityGoldenFixtures.defaultRequest(
                        input = fixture.input,
                        requireJapanesePrediction = fixture.requireJapanesePrediction,
                    ),
                )
                fixture.top1?.let { assertTop1(result.mainResults, it) }
                fixture.top5?.let { expected ->
                    assertEquals(
                        expected,
                        result.mainResults.take(expected.size).map { it.string },
                    )
                }
                if (fixture.exactReadingInTop3) {
                    AzooKeyParityGoldenFixtures.assertTopThreeContainsExactReading(
                        result.mainResults,
                        fixture.input.ifBlank { fixture.gradualInput.orEmpty() },
                    )
                }
            }
        }
    }

    @Test
    fun swiftOutputMatchesKotlinWhenAvailable() = AzooKeyParityGoldenFixtures.runWithAssets {
        val swiftOutput = File("build/azookey-swift-parity-output.json")
        assumeTrue("Swift parity output optional", swiftOutput.isFile)
        val swiftByQuery = parseSwiftOutput(swiftOutput)
        val engine = AzooKeyParityGoldenFixtures.engine()
        for ((query, expected) in swiftByQuery) {
            val kotlinTop = AzooKeyParityGoldenFixtures.convert(
                engine,
                AzooKeyParityGoldenFixtures.defaultRequest(query),
            ).mainResults.take(expected.size).map { it.string }
            assertEquals("query=$query", expected, kotlinTop)
        }
    }

    private data class FixtureJson(
        val id: String? = null,
        val input: String? = null,
        @SerializedName("gradualInput") val gradualInput: String? = null,
        val top1: String? = null,
        val top5: List<String>? = null,
        val exactReadingInTop3: Boolean = false,
        val requireJapanesePrediction: Boolean = false,
    )

    private data class Fixture(
        val input: String,
        val gradualInput: String?,
        val top1: String?,
        val top5: List<String>?,
        val exactReadingInTop3: Boolean,
        val requireJapanesePrediction: Boolean,
    )

    private fun loadFixtures(): List<Fixture> {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("azookey_parity_fixtures.json")) {
            "fixtures missing"
        }
        val items = gson.fromJson(stream.reader(), Array<FixtureJson>::class.java)
        return items.map { json ->
            Fixture(
                input = json.input.orEmpty(),
                gradualInput = json.gradualInput?.takeIf { it.isNotBlank() },
                top1 = json.top1?.takeIf { it.isNotBlank() },
                top5 = json.top5,
                exactReadingInTop3 = json.exactReadingInTop3,
                requireJapanesePrediction = json.requireJapanesePrediction,
            )
        }
    }

    private data class SwiftEvalItem(
        val query: String,
        val answer: List<String>,
    )

    private fun parseSwiftOutput(file: File): Map<String, List<String>> {
        val items = gson.fromJson(file.readText(), Array<SwiftEvalItem>::class.java)
        return items.associate { it.query to it.answer }
    }

    private fun assertTop1(candidates: List<Candidate>, expected: String) {
        assertEquals(expected, candidates.firstOrNull()?.string)
    }
}
