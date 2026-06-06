package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry

/**
 * AzooKey [LatticeNode](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当（入力 index のみ）。
 */
data class AzooKeyLatticeNode(
    val entry: AzooKeyDictionaryEntry,
    val startIndex: Int,
    val endIndex: Int,
) {
    val readingLength: Int
        get() = endIndex - startIndex
}