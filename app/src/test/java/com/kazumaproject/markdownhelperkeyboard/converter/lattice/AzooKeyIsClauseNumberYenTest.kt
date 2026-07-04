package com.kazumaproject.markdownhelperkeyboard.converter.lattice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyIsClauseNumberYenTest {
    @Test
    fun numberToEnWordClauseBoundary() {
        assertFalse(
            AzooKeyDicdataStoreUtils.isClause(formerRcid = 1295, latterLcid = 1300),
        )
        assertTrue(
            AzooKeyDicdataStoreUtils.isClause(formerRcid = 1295, latterLcid = 11),
        )
    }
}
