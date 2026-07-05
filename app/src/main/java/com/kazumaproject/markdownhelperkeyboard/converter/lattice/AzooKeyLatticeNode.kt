package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.AzooKeyDictionaryEntry

/**
 * AzooKey [LatticeNode](https://github.com/azooKey/AzooKeyKanaKanjiConverter) 相当（入力 index のみ）。
 */
class AzooKeyLatticeNode(
    val entry: AzooKeyDictionaryEntry,
    val startIndex: Int,
    val endIndex: Int,
) {
    val prevs: MutableList<RegisteredNode> = mutableListOf()
    val values: MutableList<Float> = mutableListOf()

    val readingLength: Int
        get() = endIndex - startIndex

    override fun toString(): String {
        return "AzooKeyLatticeNode(surface=${entry.surface}, reading=${entry.reading}, start=$startIndex, end=$endIndex, prevsSize=${prevs.size})"
    }
}