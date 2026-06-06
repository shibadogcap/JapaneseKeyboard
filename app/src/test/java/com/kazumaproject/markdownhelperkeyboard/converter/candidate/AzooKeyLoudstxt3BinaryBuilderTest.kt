package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyLoudstxt3BinaryBuilderTest {
    @Test
    fun makeBinaryRoundTripsThroughParser() {
        val groups = listOf(
            AzooKeyLoudstxt3BinaryBuilder.RubyGroup(
                ruby = "しかい",
                rows = listOf(
                    AzooKeyLoudstxt3BinaryBuilder.Row(
                        word = "司会",
                        leftId = 1285,
                        rightId = 1285,
                        mid = 501,
                        value = -5.5f,
                    ),
                ),
            ),
        )
        val bytes = AzooKeyLoudstxt3BinaryBuilder.makeBinary(groups)
        val parsed = AzooKeyLoudstxt3BinaryParser.parseFile(
            bytes = bytes,
            sourceKind = AzooKeyDictionarySourceKind.Memory,
        )
        assertEquals("司会", parsed.first().surface)
        assertEquals("しかい", parsed.first().reading)
    }
}