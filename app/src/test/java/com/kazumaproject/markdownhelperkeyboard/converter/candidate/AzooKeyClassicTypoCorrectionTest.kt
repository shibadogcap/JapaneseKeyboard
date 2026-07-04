package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyClassicTypoCorrectionTest {
    @Test
    fun directTypoIncludesDakutenVariant() {
        val matches = AzooKeyClassicTypoCorrection.classicTypoSearcher()
            .typoPrefixMatches("カ")
        assertTrue(matches.any { it.first == "ガ" })
    }

    @Test
    fun romanTypoIncludesAlternateReading() {
        val matches = AzooKeyClassicTypoCorrection.classicTypoSearcher()
            .typoPrefixMatches("li")
        assertTrue(matches.any { it.first.equals("ki", ignoreCase = true) })
    }

    @Test
    fun singleDirectCharacterIncludesIdentity() {
        val matches = AzooKeyClassicTypoCorrection.classicTypoSearcher()
            .typoPrefixMatches("サ")
        assertEquals(0, matches.first { it.first == "サ" }.second)
    }
}
