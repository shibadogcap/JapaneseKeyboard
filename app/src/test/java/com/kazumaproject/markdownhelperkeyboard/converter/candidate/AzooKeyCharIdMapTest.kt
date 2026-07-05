package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Test

class AzooKeyCharIdMapTest {
    @Test
    fun encodeNormalizesHiraganaToKatakanaCharIds() {
        val charIdMap = bundledCharIdMap()
        val hiragana = charIdMap.encode("しかい")
        val katakana = charIdMap.encode("シカイ")
        assertNotNull(hiragana)
        assertNotNull(katakana)
        org.junit.Assert.assertEquals(hiragana, katakana)
    }

    private fun bundledCharIdMap(): AzooKeyCharIdMap {
        val file = listOf(
            File("app/src/main/assets/louds/charID.chid"),
            File("src/main/assets/louds/charID.chid"),
        ).firstOrNull { it.isFile }
            ?: error("charID.chid is missing")
        return AzooKeyCharIdMap.parse(file.readText())
    }
}