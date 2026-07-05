package com.kazumaproject.markdownhelperkeyboard.converter.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AzooKeyRoman2KanaInputTableTest {
    @Test
    fun defaultTableMatchesAzooKeyCoreMappings() {
        val map = mapOf(
            "ka" to ("か" to 2),
            "shi" to ("し" to 3),
            "tu" to ("つ" to 2),
            "xtu" to ("っ" to 3),
            "n" to ("ん" to 1),
        )
        val transducer = AzooKeyRoman2KanaTransducer.fromDefaultInputTable(map)
        assertEquals("か", transducer.convert("ka"))
        assertEquals("し", transducer.convert("shi"))
        assertEquals("つ", transducer.convert("tu"))
        assertEquals("っ", transducer.convert("xtu"))
        assertEquals("ん", transducer.convert("n"))
    }

    @Test
    fun possibleNextsBuiltForPartialRoman() {
        val map = mapOf(
            "ka" to ("か" to 2),
            "ki" to ("き" to 2),
            "ku" to ("く" to 2),
        )
        val transducer = AzooKeyRoman2KanaTransducer.fromDefaultInputTable(map)
        val nexts = transducer.possibleNexts("k")
        assert(nexts.isNotEmpty())
    }
}
