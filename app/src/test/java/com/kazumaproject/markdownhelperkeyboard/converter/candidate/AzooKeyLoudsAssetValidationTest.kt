package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyLoudsAssetValidationTest {
    @Test
    fun validateAcceptsCompleteEscapedAndRawAssetSets() {
        val result = AzooKeyLoudsAssetValidation.validate(
            listOf(
                "charID.chid",
                "[30A2].louds",
                "[30A2].loudschars2",
                "[30A2]0.loudstxt3",
                "シ.louds",
                "シ.loudschars2",
                "シ0.loudstxt3",
            )
        )

        assertTrue(result.isValid)
        assertEquals(setOf("ア", "シ"), result.completeIdentifiers)
        assertEquals(emptySet<String>(), result.identifiersMissingLouds)
        assertEquals(emptySet<String>(), result.identifiersMissingLoudsChars2)
        assertEquals(emptySet<String>(), result.identifiersMissingShard)
    }

    @Test
    fun validateReportsMissingCharIdAndPerIdentifierGaps() {
        val result = AzooKeyLoudsAssetValidation.validate(
            listOf(
                "[30A2].louds",
                "[30A2]0.loudstxt3",
                "[30AB].loudschars2",
                "[30B5].louds",
                "[30B5].loudschars2",
            )
        )

        assertFalse(result.isValid)
        assertFalse(result.hasCharIdMap)
        assertEquals(emptySet<String>(), result.completeIdentifiers)
        assertEquals(setOf("カ"), result.identifiersMissingLouds)
        assertEquals(setOf("ア"), result.identifiersMissingLoudsChars2)
        assertEquals(setOf("カ", "サ"), result.identifiersMissingShard)
    }
}
