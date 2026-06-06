package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialCandidateProviderTest {
    @Test
    fun unicodeProviderConvertsUPrefixHexInput() {
        val candidates = UnicodeSpecialCandidateProvider.provide(baseRequest("u3042"))

        assertEquals(listOf("あ"), candidates.map { it.string })
        assertEquals(CandidateType.UNICODE_SPECIAL, candidates.first().type)
    }

    @Test
    fun unicodeProviderConvertsUPlusPrefixHexInput() {
        val candidates = UnicodeSpecialCandidateProvider.provide(baseRequest("U+1F600"))

        assertEquals(listOf("😀"), candidates.map { it.string })
    }

    @Test
    fun unicodeProviderIgnoresInvalidHexInput() {
        assertTrue(UnicodeSpecialCandidateProvider.provide(baseRequest("uXYZ")).isEmpty())
    }

    @Test
    fun defaultProvidersExposeUnicodeCandidate() {
        val candidates = DefaultSpecialCandidateProviders.provide(baseRequest("u3042"))

        assertTrue(candidates.map { it.string }.contains("あ"))
    }

    @Test
    fun versionProviderShowsVersionStringForVersionInput() {
        val candidates = VersionSpecialCandidateProvider.provide(
            baseRequest("ばーじょん", versionString = "JapaneseKeyboard Version 1.0")
        )

        assertEquals(listOf("JapaneseKeyboard Version 1.0"), candidates.map { it.string })
        assertEquals(CandidateType.VERSION_SPECIAL, candidates.first().type)
        assertEquals(false, candidates.first().isLearningTarget)
    }

    @Test
    fun versionProviderIgnoresVersionInputWithoutVersionString() {
        assertTrue(VersionSpecialCandidateProvider.provide(baseRequest("ばーじょん")).isEmpty())
    }

    @Test
    fun defaultProvidersExposeVersionCandidate() {
        val candidates = DefaultSpecialCandidateProviders.provide(
            baseRequest("バージョン", versionString = "JapaneseKeyboard Version 1.0")
        )

        assertTrue(candidates.map { it.string }.contains("JapaneseKeyboard Version 1.0"))
    }

    @Test
    fun commaSeparatedNumberProviderFormatsFourDigitInteger() {
        val candidates = CommaSeparatedNumberSpecialCandidateProvider.provide(baseRequest("1000"))

        assertEquals(listOf("1,000"), candidates.map { it.string })
        assertEquals(CandidateType.COMMA_SEPARATED_NUMBER_SPECIAL, candidates.first().type)
    }

    @Test
    fun commaSeparatedNumberProviderKeepsNegativeAndFractionalParts() {
        val candidates =
            CommaSeparatedNumberSpecialCandidateProvider.provide(baseRequest("-1234567.89"))

        assertEquals(listOf("-1,234,567.89"), candidates.map { it.string })
    }

    @Test
    fun commaSeparatedNumberProviderIgnoresShortInteger() {
        assertTrue(CommaSeparatedNumberSpecialCandidateProvider.provide(baseRequest("123")).isEmpty())
    }

    @Test
    fun commaSeparatedNumberProviderIgnoresNonAsciiDigits() {
        assertTrue(CommaSeparatedNumberSpecialCandidateProvider.provide(baseRequest("１２３４")).isEmpty())
    }

    @Test
    fun defaultProvidersExposeCommaSeparatedNumberCandidate() {
        val candidates = DefaultSpecialCandidateProviders.provide(baseRequest("1234567"))

        assertTrue(candidates.map { it.string }.contains("1,234,567"))
    }

    @Test
    fun timeExpressionProviderFormatsThreeDigitTime() {
        val candidates = TimeExpressionSpecialCandidateProvider.provide(baseRequest("930"))

        assertEquals(listOf("9:30"), candidates.map { it.string })
        assertEquals(CandidateType.TIME_EXPRESSION_SPECIAL, candidates.first().type)
    }

    @Test
    fun timeExpressionProviderFormatsFourDigitTime() {
        val candidates = TimeExpressionSpecialCandidateProvider.provide(baseRequest("0930"))

        assertEquals(listOf("09:30"), candidates.map { it.string })
    }

    @Test
    fun timeExpressionProviderAllowsAzooKeyBoundaryHour24() {
        val candidates = TimeExpressionSpecialCandidateProvider.provide(baseRequest("2459"))

        assertEquals(listOf("24:59"), candidates.map { it.string })
    }

    @Test
    fun timeExpressionProviderIgnoresInvalidMinute() {
        assertTrue(TimeExpressionSpecialCandidateProvider.provide(baseRequest("0960")).isEmpty())
    }

    @Test
    fun calendarProviderConvertsSeirekiToReiwa() {
        val candidates = CalendarSpecialCandidateProvider.provide(baseRequest("2024ねん"))

        assertEquals(listOf("令和6年"), candidates.map { it.string })
        assertEquals(CandidateType.CALENDAR_SPECIAL, candidates.first().type)
    }

    @Test
    fun calendarProviderConvertsBoundaryYearToMultipleWarekiCandidates() {
        val candidates = CalendarSpecialCandidateProvider.provide(baseRequest("2019ねん"))

        assertEquals(listOf("令和元年", "平成31年"), candidates.map { it.string })
    }

    @Test
    fun calendarProviderConvertsGannenToSeireki() {
        val candidates = CalendarSpecialCandidateProvider.provide(baseRequest("れいわがんねん"))

        assertEquals(listOf("2019年"), candidates.map { it.string })
    }

    @Test
    fun calendarProviderConvertsWarekiNumberToSeireki() {
        val candidates = CalendarSpecialCandidateProvider.provide(baseRequest("へいせい31ねん"))

        assertEquals(listOf("2019年"), candidates.map { it.string })
    }

    @Test
    fun calendarProviderIgnoresYearWithoutNenSuffix() {
        assertTrue(CalendarSpecialCandidateProvider.provide(baseRequest("2024")).isEmpty())
    }

    @Test
    fun emailAddressProviderSuggestsDomainsForAtSuffix() {
        val candidates = EmailAddressSpecialCandidateProvider.provide(baseRequest("sumire@"))

        assertEquals(
            listOf("sumire@gmail.com", "sumire@icloud.com", "sumire@yahoo.co.jp"),
            candidates.take(3).map { it.string }
        )
        assertEquals(CandidateType.EMAIL_ADDRESS_SPECIAL, candidates.first().type)
    }

    @Test
    fun emailAddressProviderFiltersByDomainPrefix() {
        val candidates = EmailAddressSpecialCandidateProvider.provide(baseRequest("sumire@out"))

        assertEquals(listOf("sumire@outlook.com", "sumire@outlook.jp"), candidates.map { it.string })
    }

    @Test
    fun emailAddressProviderAllowsEmptyIdLikeAzooKey() {
        val candidates = EmailAddressSpecialCandidateProvider.provide(baseRequest("@g"))

        assertEquals(listOf("@gmail.com", "@googlemail.com"), candidates.map { it.string })
    }

    @Test
    fun emailAddressProviderIgnoresJapaneseId() {
        assertTrue(EmailAddressSpecialCandidateProvider.provide(baseRequest("すみれ@")).isEmpty())
    }

    @Test
    fun symbolProviderSuggestsCommonSymbols() {
        val candidates = SymbolSpecialCandidateProvider.provide(baseRequest("きごう"))

        assertEquals(listOf("♪", "☆", "★"), candidates.take(3).map { it.string })
        assertEquals(CandidateType.SYMBOL_SPECIAL, candidates.first().type)
    }

    @Test
    fun symbolProviderSuggestsArrowSymbols() {
        val candidates = SymbolSpecialCandidateProvider.provide(baseRequest("やじるし"))

        assertEquals(listOf("→", "←", "↑", "↓"), candidates.take(4).map { it.string })
    }

    @Test
    fun defaultProvidersExposeSymbolCandidate() {
        val candidates = DefaultSpecialCandidateProviders.provide(baseRequest("かっこ"))

        assertTrue(candidates.map { it.string }.contains("「」"))
    }

    @Test
    fun typographyProviderCreatesAlphabetVariantsInAzooKeyOrder() {
        val candidates = TypographySpecialCandidateProvider.provide(baseRequest("Az"))

        assertEquals(CandidateType.TYPOGRAPHY_SPECIAL, candidates.first().type)
        assertEquals("𝐀𝐳", candidates[0].string)
        assertEquals("𝐴𝑧", candidates[1].string)
        assertEquals("𝑨𝒛", candidates[2].string)
    }

    @Test
    fun typographyProviderCreatesDigitCapableVariantsOnlyForAlphaNumericInput() {
        val candidates = TypographySpecialCandidateProvider.provide(baseRequest("A1"))

        assertEquals(listOf("𝐀𝟏", "𝔸𝟙", "𝖠𝟣", "𝗔𝟭", "𝙰𝟷"), candidates.map { it.string })
    }

    @Test
    fun typographyProviderIgnoresNonAsciiAlphabet() {
        assertTrue(TypographySpecialCandidateProvider.provide(baseRequest("あ")).isEmpty())
    }

    private fun baseRequest(
        input: String,
        versionString: String? = null,
    ): CandidateRequest {
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
            versionString = versionString,
        )
    }
}
