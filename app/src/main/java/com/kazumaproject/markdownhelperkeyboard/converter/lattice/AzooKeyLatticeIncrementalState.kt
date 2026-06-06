package com.kazumaproject.markdownhelperkeyboard.converter.lattice

/**
 * AzooKey 本家の incremental conversion 用 lattice node キャッシュ。
 * [com.kazumaproject.markdownhelperkeyboard.converter.api.ConversionSession] が保持する。
 */
class AzooKeyLatticeIncrementalState {
    var normalizedInput: String? = null
    var latticeNodes: List<AzooKeyLatticeNode> = emptyList()

    fun clear() {
        normalizedInput = null
        latticeNodes = emptyList()
    }
}