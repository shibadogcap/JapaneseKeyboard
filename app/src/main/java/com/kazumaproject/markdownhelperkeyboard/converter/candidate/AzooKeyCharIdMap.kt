package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.core.domain.extensions.hiraganaToKatakana

class AzooKeyCharIdMap private constructor(
    private val charToId: Map<Char, Int>,
    private val idToChar: Map<Int, Char>,
) {
    fun idOf(char: Char): Int? {
        return charToId[char]
    }

    fun charOf(id: Int): Char? {
        return idToChar[id]
    }

    fun encode(text: String): List<Int>? {
        return text.hiraganaToKatakana().map { char -> charToId[char] ?: return null }
    }

    companion object {
        fun parse(text: String): AzooKeyCharIdMap {
            val chars = text.toList()
            return AzooKeyCharIdMap(
                charToId = chars.withIndex().associate { it.value to it.index },
                idToChar = chars.withIndex().associate { it.index to it.value },
            )
        }
    }
}
