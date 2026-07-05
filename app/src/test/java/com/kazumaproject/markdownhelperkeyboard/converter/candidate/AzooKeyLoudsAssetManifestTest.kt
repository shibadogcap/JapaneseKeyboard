package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyLoudsAssetManifestTest {
    @Test
    fun discoverIdentifiersUsesCompleteLoudsFilePairs() {
        val identifiers = AzooKeyLoudsAssetManifest.discoverIdentifiers(
            listOf(
                "charID.chid",
                "[30A2].louds",
                "[30A2].loudschars2",
                "[30A2]0.loudstxt3",
                "[30A4].louds",
                "[30A6].loudschars2",
            )
        )

        assertEquals(setOf("ア"), identifiers)
    }

    @Test
    fun discoverIdentifiersSupportsRawAzooKeyFixtureNames() {
        val identifiers = AzooKeyLoudsAssetManifest.discoverIdentifiers(
            listOf(
                "シ.louds",
                "シ.loudschars2",
                "シ0.loudstxt3",
            )
        )

        assertEquals(setOf("シ"), identifiers)
    }

    @Test
    fun toManifestTextSortsIdentifiersForStableAssets() {
        val text = AzooKeyLoudsAssetManifest.toManifestText(listOf("カ", "ア", "サ"))

        assertEquals("ア\nカ\nサ\n", text)
    }
}
