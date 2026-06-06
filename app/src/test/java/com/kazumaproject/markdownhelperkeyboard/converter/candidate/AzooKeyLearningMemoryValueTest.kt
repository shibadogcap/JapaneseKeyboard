package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertTrue
import org.junit.Test

class AzooKeyLearningMemoryValueTest {
    @Test
    fun pValueIsNegativeAndIncreasesWithReadingLength() {
        val short = AzooKeyLearningMemoryValue.pValue(readingLength = 1)
        val long = AzooKeyLearningMemoryValue.pValue(readingLength = 4)
        assertTrue(short < 0f)
        assertTrue(long < 0f)
        assertTrue(long > short)
    }
}