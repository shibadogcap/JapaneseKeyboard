package com.kazumaproject.markdownhelperkeyboard.converter.core

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleLearningType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStylePredictionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyStyleTypoCorrectionMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequest
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateRequestMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.DefaultSpecialCandidateProviders
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.SymbolSpecialCandidateProvider
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.TypographySpecialCandidateProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候補 surface フィルタの網羅監査。
 * 特殊候補・AzooKey 記号グループ由来の文字がエンジン検証を通過するかを確認する。
 */
class AzooKeyJapaneseConversionTextAuditTest {
  @Test
  fun allSymbolSpecialCandidateSurfacesPassValidation() {
    val readings = listOf("きごう", "やじるし", "かっこ", "まる", "ほし")
    val failures = readings.flatMap { reading ->
      SymbolSpecialCandidateProvider.provide(baseRequest(reading))
        .map { it.string }
        .filterNot { AzooKeyJapaneseConversionText.isValidCandidateSurface(it) }
        .map { reading to it }
    }
    assertTrue("blocked symbol special surfaces: $failures", failures.isEmpty())
  }

  @Test
  fun typographySpecialCandidateSurfacesPassValidation() {
    val inputs = listOf("Az", "A1", "abc")
    val failures = inputs.flatMap { input ->
      TypographySpecialCandidateProvider.provide(baseRequest(input))
        .map { it.string }
        .filterNot { AzooKeyJapaneseConversionText.isValidCandidateSurface(it) }
        .map { input to it }
    }
    assertTrue("blocked typography surfaces: $failures", failures.isEmpty())
  }

  @Test
  fun unicodeSpecialHiraganaAndEmojiPassValidation() {
    val samples = listOf("u3042", "U+1F600", "u2192", "u266A")
    val failures = samples.mapNotNull { input ->
      val surface = DefaultSpecialCandidateProviders.provide(baseRequest(input))
        .firstOrNull()
        ?.string
        ?: return@mapNotNull input to "missing"
      if (!AzooKeyJapaneseConversionText.isValidCandidateSurface(surface)) {
        input to surface
      } else {
        null
      }
    }
    assertTrue("blocked unicode special surfaces: $failures", failures.isEmpty())
  }

  @Test
  fun azooKeyWeakRelatingSymbolsCoverageAudit() {
    // AzooKey DicdataStore.weakRelatingSymbolGroups から代表的な記号を抽出
    val samples = listOf(
      "♡", "☾", "☽", "¢", "€", "₿", "‰", "°", "℃", "℉",
      "※", "…", "‥", "•", "±", "⊕", "×", "❌", "÷", "➗",
      "≦", "≪", "≧", "≫", "≒", "≠", "≡",
      "❗", "❓", "〒", "♂", "♀", "♯", "♭", "♫", "√", "∛",
      "⇆", "↪", "↩",
    )
    val failures = samples.filterNot { AzooKeyJapaneseConversionText.isValidCandidateSurface(it) }
    assertTrue("AzooKey symbol samples blocked: $failures", failures.isEmpty())
  }

  @Test
  fun engineAcceptsUnicodeSpecialEvenWhenSurfacePolicyWouldBlock() {
    val candidate = DefaultSpecialCandidateProviders.provide(baseRequest("u0410")).first()
    assertFalse(AzooKeyJapaneseConversionText.isValidCandidateSurface(candidate.string))
    assertTrue(AzooKeyJapaneseConversionText.shouldAcceptEngineCandidate(candidate))
  }

  @Test
  fun engineRejectsHangulSpecialCandidate() {
    val candidate = DefaultSpecialCandidateProviders.provide(baseRequest("uD55C")).first()
    assertFalse(AzooKeyJapaneseConversionText.shouldAcceptEngineCandidate(candidate))
  }

  @Test
  fun rejectsHangulAndMixedHangulSurfaces() {
    assertFalse(AzooKeyJapaneseConversionText.isValidCandidateSurface("한국"))
    assertFalse(AzooKeyJapaneseConversionText.isValidCandidateSurface("東京한국"))
    assertFalse(AzooKeyJapaneseConversionText.isValidCandidateSurface("→한"))
  }

  @Test
  fun zenzPrefixRemainsStricterThanClauseSurface() {
    assertTrue(AzooKeyJapaneseConversionText.isValidPrefix("東京"))
    assertFalse(AzooKeyJapaneseConversionText.isValidPrefix("→"))
    assertFalse(AzooKeyJapaneseConversionText.isValidPrefix("😀"))
    assertFalse(AzooKeyJapaneseConversionText.isValidPrefix("♪"))
  }

  private fun baseRequest(input: String): CandidateRequest {
    return CandidateRequest(
      input = input,
      mode = CandidateRequestMode.Normal,
      nBest = 10,
      useUserDictionary = true,
      useUserTemplate = true,
      useRomajiCandidates = true,
      useBunsetsu = false,
      useOmissionSearch = false,
      japanesePredictionMode = AzooKeyStylePredictionMode.AutoMix,
      englishPredictionMode = AzooKeyStylePredictionMode.Disabled,
      learningType = AzooKeyStyleLearningType.OnlyOutput,
      typoCorrectionMode = AzooKeyStyleTypoCorrectionMode.Automatic,
    )
  }
}
