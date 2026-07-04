package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Swift [ConverterTests.testMustCases] と同等の必須変換ケース。
 */
class AzooKeyMustCasesTest {
    private data class Case(val input: String, val expect: String)

    private val directCases = listOf(
        Case("つかっている", "使っている"),
        Case("しんだどうぶつ", "死んだ動物"),
        Case("けいさん", "計算"),
        Case("azooKeyをつかう", "azooKeyを使う"),
        Case("じどうAIそうじゅう。", "自動AI操縦。"),
        Case("1234567890123456789012", "1234567890123456789012"),
    )

    @Test
    fun directInputFullConversion() = AzooKeyParityGoldenFixtures.runWithAssets {
        for (case in directCases) {
            val engine = AzooKeyParityGoldenFixtures.engine()
            val result = AzooKeyParityGoldenFixtures.convert(
                engine,
                AzooKeyParityGoldenFixtures.defaultRequest(case.input),
            )
            assertEquals("input=${case.input}", case.expect, result.mainResults.firstOrNull()?.string)
        }
    }

    @Test
    fun directInputGradualConversion() = AzooKeyParityGoldenFixtures.runWithAssets {
        for (case in directCases) {
            val engine = AzooKeyParityGoldenFixtures.engine()
            var text = ""
            for (ch in case.input) {
                text += ch
                val result = AzooKeyParityGoldenFixtures.convert(
                    engine,
                    AzooKeyParityGoldenFixtures.defaultRequest(text),
                )
                if (text.length == case.input.length) {
                    assertEquals("input=${case.input}", case.expect, result.mainResults.firstOrNull()?.string)
                }
            }
        }
    }

    private val typoCases = listOf(
        Case("たいかくせい", "大学生"),
        Case("きみのことかすき", "君のことが好き"),
        Case("おへんとうをもつていく", "お弁当を持っていく"),
    )

    private fun typoRequest(input: String) = AzooKeyParityGoldenFixtures.defaultRequest(input).copy(
        typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Enabled,
    )

    @Test
    fun typoCorrectionFullConversion() = AzooKeyParityGoldenFixtures.runWithAssets {
        for (case in typoCases) {
            val engine = AzooKeyParityGoldenFixtures.engine()
            val result = AzooKeyParityGoldenFixtures.convert(
                engine,
                typoRequest(case.input),
            )
            val top = result.mainResults.take(5).map { it.string }
            org.junit.Assert.assertTrue(
                "input=${case.input} expect=${case.expect} got=$top",
                case.expect in top,
            )
        }
    }

    // TypoCorrectionGenerator の DicdataStore 統合後に Swift testMustCases typo gradual を有効化する
}
