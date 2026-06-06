package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemDictionarySourcePolicyTest {
    @Test
    fun resolvesLatticePrimaryWhenLoudsAndConnectionCostAvailable() {
        assertEquals(
            SystemDictionarySourcePolicy.AzooKeyLatticePrimary,
            resolveSystemDictionarySourcePolicy(
                loudsRegistryAvailable = true,
                connectionCostStoreAvailable = true,
            ),
        )
    }

    @Test
    fun resolvesLoudsPrimaryWhenLoudsAvailableWithoutConnectionCost() {
        assertEquals(
            SystemDictionarySourcePolicy.AzooKeyLoudsPrimary,
            resolveSystemDictionarySourcePolicy(
                loudsRegistryAvailable = true,
                connectionCostStoreAvailable = false,
            ),
        )
    }

    @Test
    fun resolvesEngineOnlyWhenRegistryMissing() {
        assertEquals(
            SystemDictionarySourcePolicy.KanaKanjiEngineOnly,
            resolveSystemDictionarySourcePolicy(loudsRegistryAvailable = false),
        )
    }

    @Test
    fun explicitPolicyOverridesAuto() {
        assertEquals(
            SystemDictionarySourcePolicy.DualPath,
            resolveSystemDictionarySourcePolicy(
                loudsRegistryAvailable = true,
                connectionCostStoreAvailable = true,
                explicit = SystemDictionarySourcePolicy.DualPath,
            ),
        )
    }
}