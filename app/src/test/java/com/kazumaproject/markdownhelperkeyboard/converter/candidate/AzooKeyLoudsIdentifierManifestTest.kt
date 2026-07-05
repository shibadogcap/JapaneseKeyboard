package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyLoudsIdentifierManifestTest {
    @Test
    fun parseReadsWhitespaceCommaAndComments() {
        val result = AzooKeyLoudsIdentifierManifest.parse(
            """
            # AzooKey louds identifiers
            ア イ,ウ
            エ	オ # inline comment
            
            user
            """.trimIndent()
        )

        assertEquals(setOf("ア", "イ", "ウ", "エ", "オ", "user"), result)
    }

    @Test
    fun parseKeepsHashIdentifierWhenLineIsOnlyHash() {
        val result = AzooKeyLoudsIdentifierManifest.parse(
            """
            # comment
            #
            ア # inline comment
            """.trimIndent()
        )

        assertEquals(setOf("#", "ア"), result)
    }
}
