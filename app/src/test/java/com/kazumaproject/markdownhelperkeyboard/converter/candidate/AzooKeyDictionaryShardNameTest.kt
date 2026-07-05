package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyDictionaryShardNameTest {
    @Test
    fun escapedIdentifierMatchesAzooKeyUtf16HexChunks() {
        assertEquals("[0061]", AzooKeyDictionaryShardName.escapedIdentifier("a"))
        assertEquals("[0041]", AzooKeyDictionaryShardName.escapedIdentifier("A"))
        assertEquals("[0020]", AzooKeyDictionaryShardName.escapedIdentifier(" "))
        assertEquals("[002F]", AzooKeyDictionaryShardName.escapedIdentifier("/"))
        assertEquals("[3042]", AzooKeyDictionaryShardName.escapedIdentifier("あ"))
        assertEquals("[30A2]", AzooKeyDictionaryShardName.escapedIdentifier("ア"))
        assertEquals("[6F22]", AzooKeyDictionaryShardName.escapedIdentifier("漢"))
        assertEquals("[D83C_DDEF_D83C_DDF5]", AzooKeyDictionaryShardName.escapedIdentifier("🇯🇵"))
    }

    @Test
    fun escapedIdentifierKeepsAzooKeySpecialRawIdentifiers() {
        assertEquals("user", AzooKeyDictionaryShardName.escapedIdentifier("user"))
        assertEquals("memory", AzooKeyDictionaryShardName.escapedIdentifier("memory"))
        assertEquals("user_shortcuts", AzooKeyDictionaryShardName.escapedIdentifier("user_shortcuts"))
    }

    @Test
    fun unescapedIdentifierRestoresAzooKeyUtf16HexChunks() {
        assertEquals("a", AzooKeyDictionaryShardName.unescapedIdentifier("[0061]"))
        assertEquals("あ", AzooKeyDictionaryShardName.unescapedIdentifier("[3042]"))
        assertEquals("ア", AzooKeyDictionaryShardName.unescapedIdentifier("[30A2]"))
        assertEquals("漢", AzooKeyDictionaryShardName.unescapedIdentifier("[6F22]"))
        assertEquals("🇯🇵", AzooKeyDictionaryShardName.unescapedIdentifier("[D83C_DDEF_D83C_DDF5]"))
    }

    @Test
    fun identifierFromFileStemSupportsRawAndEscapedNames() {
        assertEquals("シ", AzooKeyDictionaryShardName.identifierFromFileStem("シ"))
        assertEquals("シ", AzooKeyDictionaryShardName.identifierFromFileStem("[30B7]"))
        assertEquals("user", AzooKeyDictionaryShardName.identifierFromFileStem("user"))
    }

    @Test
    fun loudstxt3FileNameCombinesEscapedIdentifierAndShardIndex() {
        assertEquals("[3042]0.loudstxt3", AzooKeyDictionaryShardName.loudstxt3FileName("あ", 0))
        assertEquals("user2.loudstxt3", AzooKeyDictionaryShardName.loudstxt3FileName("user", 2))
    }

    @Test
    fun loudsFileNamesUseEscapedAzooKeyIdentifier() {
        assertEquals("[3042].louds", AzooKeyDictionaryShardName.loudsFileName("あ"))
        assertEquals("[3042].loudschars2", AzooKeyDictionaryShardName.loudsChars2FileName("あ"))
        assertEquals("memory.louds", AzooKeyDictionaryShardName.loudsFileName("memory"))
    }

    @Test
    fun rawFileNamesSupportAzooKeyLegacyFixtures() {
        assertEquals("シ0.loudstxt3", AzooKeyDictionaryShardName.rawLoudstxt3FileName("シ", 0))
        assertEquals("シ.louds", AzooKeyDictionaryShardName.rawLoudsFileName("シ"))
        assertEquals("シ.loudschars2", AzooKeyDictionaryShardName.rawLoudsChars2FileName("シ"))
    }
}
