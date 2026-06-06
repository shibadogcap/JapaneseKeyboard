package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * AzooKey 互換の system 辞書供給方針。
 *
 * - [AzooKeyLoudsPrimary]: 同梱 LOUDS を system 主（フラット lookup）
 * - [AzooKeyLatticePrimary]: LOUDS + memory を lattice/Viterbi で競合（本家寄せ）
 * - [KanaKanjiEngineOnly]: LOUDS 未ロード時のフォールバック
 * - [DualPath]: 移行用。LOUDS auxiliary + engine system の二重経路
 */
enum class SystemDictionarySourcePolicy {
    AzooKeyLoudsPrimary,
    AzooKeyLatticePrimary,
    KanaKanjiEngineOnly,
    DualPath,
}

fun resolveSystemDictionarySourcePolicy(
    loudsRegistryAvailable: Boolean,
    connectionCostStoreAvailable: Boolean = false,
    explicit: SystemDictionarySourcePolicy? = null,
): SystemDictionarySourcePolicy {
    explicit?.let { return it }
    return when {
        !loudsRegistryAvailable -> SystemDictionarySourcePolicy.KanaKanjiEngineOnly
        connectionCostStoreAvailable -> SystemDictionarySourcePolicy.AzooKeyLatticePrimary
        else -> SystemDictionarySourcePolicy.AzooKeyLoudsPrimary
    }
}