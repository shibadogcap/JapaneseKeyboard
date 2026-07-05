package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kazumaproject.markdownhelperkeyboard.converter.api.AzooKeyRoman2KanaTransducer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Swift [ConverterTests.testMustCases] 相当（1文字ずつ direct / roman2kana 入力）と Kotlin の top-N 一致を検証。
 * `scripts/azookey-swift-sequential-parity.sh` で生成した JSON が必要。
 */
class AzooKeySwiftSequentialParityTest {
    private val gson = Gson()

    @Test
    fun kotlinTopMatchesSwiftSequentialOutput() = AzooKeyParityGoldenFixtures.runWithAssets {
        val swiftFile = File("build/azookey-swift-sequential-output.json")
        assumeTrue("Run scripts/azookey-swift-sequential-parity.sh on macOS", swiftFile.isFile)
        val swiftItems = gson.fromJson(swiftFile.readText(), Array<SwiftItem>::class.java)
        val engine = AzooKeyParityGoldenFixtures.engine()
        val roman2Kana = AzooKeyParityGoldenFixtures.defaultRoman2KanaTransducer()
        for (item in swiftItems) {
            engine.stopComposition()
            val composingText = when {
                item.roman2kana -> AzooKeyParityGoldenFixtures.buildSequentialRoman2KanaComposingText(
                    item.query,
                    roman2Kana,
                )
                else -> AzooKeyParityGoldenFixtures.buildSequentialDirectComposingText(item.query)
            }
            val transducer = if (item.roman2kana) roman2Kana else AzooKeyRoman2KanaTransducer.Identity
            val request = AzooKeyParityGoldenFixtures.defaultRequest(
                input = item.query,
                composingText = composingText,
            ).copy(
                typoCorrectionMode = if (item.typo) {
                    AzooKeyStyleTypoCorrectionMode.Enabled
                } else {
                    AzooKeyStyleTypoCorrectionMode.Disabled
                },
            )
            if (item.convertTarget != null) {
                assertEquals(
                    "convertTarget query=${item.query}",
                    item.convertTarget,
                    composingText.convertTarget,
                )
            }
            val kotlinTop = AzooKeyParityGoldenFixtures.convert(
                engine = engine,
                request = request,
                swiftAlignedOptions = true,
                roman2KanaTransducer = transducer,
            ).mainResults.take(item.top.size).map { it.string }
            assertEquals("query=${item.query}", item.top, kotlinTop)
        }
    }

    private data class SwiftItem(
        val query: String,
        val top: List<String>,
        @SerializedName("typo") val typo: Boolean = false,
        @SerializedName("roman2kana") val roman2kana: Boolean = false,
        @SerializedName("convertTarget") val convertTarget: String? = null,
    )
}
